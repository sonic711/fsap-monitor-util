package com.fsap.monitor.core.report;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.viewsync.ViewSyncService;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
/**
 * 執行報表 SQL，產出最終 Excel workbook 與對應 CSV 產物。
 *
 * <p>每次執行都會放進獨立的 timestamp 輸出目錄，並且額外保存當次實際解析後的
 * 參數快照，方便未來除錯與重現同一批報表。
 */
public class ReportGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String REPORT_LOG_FILE = "report_execution.log";
    private static final String REPORT_PARAMS_FILE = "report-params.json";
    private static final String NO_DATA_MESSAGE_HEADER = "Message";
    private static final String WORKBOOK_PREFIX = "維運月度報表_彙總_";
    private static final Pattern REPORT_ORDER_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)\\.?\\s*");
    private static final Pattern REPORT_PARAMETER_PATTERN = Pattern.compile("\\$\\{([a-zA-Z][a-zA-Z0-9]*)}");
    private static final String INTEGER_NUMBER_FORMAT = "#,##0";
    private static final String DECIMAL_NUMBER_FORMAT = "#,##0.############";

    private final ProjectPathService projectPathService;
    private final DuckDbConnectionFactory connectionFactory;
    private final ViewSyncService viewSyncService;
    private final ReportParameterDefaultsService reportParameterDefaultsService;
    private final ObjectMapper objectMapper;

    public ReportGenerationService(
            ProjectPathService projectPathService,
            DuckDbConnectionFactory connectionFactory,
            ViewSyncService viewSyncService,
            ReportParameterDefaultsService reportParameterDefaultsService,
            ObjectMapper objectMapper
    ) {
        this.projectPathService = projectPathService;
        this.connectionFactory = connectionFactory;
        this.viewSyncService = viewSyncService;
        this.reportParameterDefaultsService = reportParameterDefaultsService;
        this.objectMapper = objectMapper;
    }

    /**
     * 依據輸入參數產生一批完整報表。
     */
    public ReportGenerationResult generate(ReportGenerationRequest request) {
        ReportGenerationRequest effectiveRequest = reportParameterDefaultsService.resolve(request);
        String timestamp = effectiveRequest.timestamp() != null
                ? effectiveRequest.timestamp()
                : LocalDateTime.now().format(TIMESTAMP_FORMAT);

        Path runDirectory = projectPathService.reportOutputDir().resolve(timestamp);
        Path workbookPath = runDirectory.resolve(WORKBOOK_PREFIX + timestamp + ".xlsx");
        Path parametersFile = runDirectory.resolve(REPORT_PARAMS_FILE);

        try {
            Files.createDirectories(projectPathService.reportOutputDir());
            Files.createDirectories(projectPathService.logDir());
            Files.createDirectories(runDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create report output directories", exception);
        }

        logExecution("=== FSAP 報表 Step 2 啟動 ===");
        logExecution("Base directory: " + projectPathService.baseDir());
        logExecution("Run directory: " + runDirectory);
        logExecution("Report parameters: " + String.join(", ", effectiveRequest.summaryParts()));

        List<Path> reportFiles = scanReportFiles();
        List<String> failures = new ArrayList<>();
        List<ReportFileResult> reportResults = new ArrayList<>();
        Throwable fatalFailure = null;

        writeParametersFile(parametersFile, effectiveRequest);

        try (Connection connection = connectionFactory.openConnection();
             XSSFWorkbook workbook = new XSSFWorkbook()) {
            logExecution("✅ [資料庫] 已連接實體庫: " + projectPathService.databaseFile().getFileName());
            // POI 的 CellStyle 只能屬於單一 workbook。每次重新建立可以避免
            // 第二次產報時誤用上一份 workbook style 的錯誤。
            WorkbookStyles workbookStyles = createWorkbookStyles(workbook);

            loadMacros(connection);
            syncViews(connection);

            Set<String> usedSheetNames = new HashSet<>();
            for (Path reportFile : reportFiles) {
                String baseName = stripSqlExtension(reportFile.getFileName().toString());
                String sheetName = nextSheetName(baseName, usedSheetNames);
                logExecution("📝 [處理中] " + reportFile.getFileName());

                try {
                    QueryTable table = executeReportQuery(connection, reportFile, effectiveRequest);
                    writeWorkbookSheet(workbook, workbookStyles, sheetName, table);
                    writeCsv(runDirectory.resolve(baseName + ".csv"), table);
                    reportResults.add(new ReportFileResult(baseName, table.rows().size(), true, null));
                    logExecution("  ✅ 完成 (" + table.rows().size() + " 筆)");
                } catch (Exception exception) {
                    String errorMessage = reportFile.getFileName() + ": " + exception.getMessage();
                    failures.add(errorMessage);
                    reportResults.add(new ReportFileResult(baseName, 0, false, exception.getMessage()));
                    // 把失敗資訊直接寫進 workbook，這樣操作人員即使不看 server log，
                    // 也能從報表成品本身知道哪一頁出了什麼問題。
                    writeErrorSheet(workbook, workbookStyles, nextSheetName("ERR_" + baseName, usedSheetNames), exception.getMessage());
                    logExecution("  ❌ 失敗: " + exception.getMessage());
                    if (!effectiveRequest.continueOnError()) {
                        fatalFailure = exception;
                        break;
                    }
                }
            }

            try (OutputStream outputStream = Files.newOutputStream(workbookPath)) {
                workbook.write(outputStream);
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Report generation failed", exception);
        }

        logExecution("📊 彙總報表: " + workbookPath.getFileName());
        logExecution("✨ 產出完成: success=" + successCount(reportResults) + " failure=" + failures.size());

        if (fatalFailure != null) {
            throw new IllegalStateException("Report generation stopped after first failure", fatalFailure);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Report generation completed with failures");
        }

        return new ReportGenerationResult(timestamp, runDirectory, workbookPath, parametersFile, effectiveRequest, reportResults, failures);
    }

    private List<Path> scanReportFiles() {
        Path reportsDir = projectPathService.reportsDir();
        if (!Files.isDirectory(reportsDir)) {
            throw new IllegalStateException("Reports directory not found: " + reportsDir);
        }

        try (Stream<Path> stream = Files.list(reportsDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    // workbook 分頁順序必須符合報表檔名上的數字編號慣例。
                    .sorted(reportFileComparator())
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to scan reports directory: " + reportsDir, exception);
        }
    }

    private Comparator<Path> reportFileComparator() {
        return (left, right) -> {
            String leftName = stripSqlExtension(left.getFileName().toString());
            String rightName = stripSqlExtension(right.getFileName().toString());

            List<Integer> leftOrder = extractReportOrder(leftName);
            List<Integer> rightOrder = extractReportOrder(rightName);

            // 逐段數字比較才能得到 1 < 1.1 < 2 的結果，避免單純字串排序時
            // 出現 "10" 排在 "2" 前面的問題。
            int segmentCount = Math.min(leftOrder.size(), rightOrder.size());
            for (int index = 0; index < segmentCount; index++) {
                int compare = Integer.compare(leftOrder.get(index), rightOrder.get(index));
                if (compare != 0) {
                    return compare;
                }
            }

            if (!leftOrder.equals(rightOrder)) {
                return Integer.compare(leftOrder.size(), rightOrder.size());
            }

            return leftName.compareTo(rightName);
        };
    }

    private List<Integer> extractReportOrder(String fileName) {
        Matcher matcher = REPORT_ORDER_PREFIX.matcher(fileName);
        if (!matcher.find()) {
            return List.of(Integer.MAX_VALUE);
        }

        return Stream.of(matcher.group(1).split("\\."))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }

    private void loadMacros(Connection connection) {
        Path macrosDir = projectPathService.macrosDir();
        if (!Files.isDirectory(macrosDir)) {
            logExecution("ℹ️ [工具] 無 macros 目錄，略過");
            return;
        }

        try (Stream<Path> stream = Files.list(macrosDir)) {
            List<Path> macroFiles = stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());

            for (Path macroFile : macroFiles) {
                String sql = projectPathService.rewriteProjectRelativePaths(Files.readString(macroFile));
                try (Statement statement = connection.createStatement()) {
                    statement.execute(sql);
                    logExecution("🔧 [註冊工具] " + macroFile.getFileName());
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load macros", exception);
        }
    }

    private void syncViews(Connection connection) {
        ViewSyncService.ViewSyncResult result = viewSyncService.syncViews(connection, null, false);
        logExecution("✅ [載入視圖] success=" + result.successCount() + " failure=" + result.failureCount());
        if (result.failureCount() > 0) {
            result.failures().forEach(failure -> logExecution("  - " + failure));
            throw new IllegalStateException("View sync finished with failures");
        }
    }

    private QueryTable executeReportQuery(Connection connection, Path reportFile, ReportGenerationRequest request) throws Exception {
        String sql = Files.readString(reportFile);
        sql = renderReportParameters(sql, request);
        sql = projectPathService.rewriteProjectRelativePaths(sql);
        try (Statement statement = connection.createStatement()) {
            try {
                boolean hasResultSet = statement.execute(sql);
                if (!hasResultSet) {
                    throw new IllegalStateException("SQL did not return a result set");
                }

                try (ResultSet resultSet = statement.getResultSet()) {
                    return readResultSet(resultSet);
                }
            } catch (SQLException exception) {
                // 動態 pivot 報表在選定期間完全沒有資料時，DuckDB 會因為展不出欄位而失敗。
                // 這裡改為輸出可讀提示頁，而不是讓整批報表直接中止。
                if (looksLikePivotSql(sql) && isEmptyProjectionBinderError(exception)) {
                    logExecution("  ℹ️ 無資料可供動態 PIVOT 展開，改輸出提示頁: " + reportFile.getFileName());
                    return noDataTable(reportFile);
                }
                throw exception;
            }
        }
    }

    private String renderReportParameters(String sql, ReportGenerationRequest request) {
        Matcher matcher = REPORT_PARAMETER_PATTERN.matcher(sql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String parameterName = matcher.group(1);
            String value = request.templateParameters().get(parameterName);
            if (value == null) {
                throw new IllegalStateException("Unknown or unresolved report parameter: " + parameterName);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private QueryTable readResultSet(ResultSet resultSet) throws Exception {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> headers = new ArrayList<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            headers.add(metadata.getColumnLabel(index));
        }

        List<List<Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<Object> row = new ArrayList<>(metadata.getColumnCount());
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                row.add(resultSet.getObject(index));
            }
            rows.add(row);
        }
        return new QueryTable(headers, rows);
    }

    private void writeWorkbookSheet(XSSFWorkbook workbook, WorkbookStyles workbookStyles, String sheetName, QueryTable table) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < table.headers().size(); index++) {
            headerRow.createCell(index).setCellValue(table.headers().get(index));
        }

        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<Object> values = table.rows().get(rowIndex);
            for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                writeCellValue(workbookStyles, row.createCell(columnIndex), values.get(columnIndex));
            }
        }

        adjustColumnWidths(sheet, table);
    }

    private void writeErrorSheet(XSSFWorkbook workbook, WorkbookStyles workbookStyles, String sheetName, String message) {
        QueryTable errorTable = new QueryTable(List.of("Error"), List.of(List.of(message)));
        writeWorkbookSheet(workbook, workbookStyles, sheetName, errorTable);
    }

    private void writeCellValue(WorkbookStyles workbookStyles, Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Boolean booleanValue) {
            cell.setCellValue(booleanValue);
            return;
        }
        if (value instanceof BigInteger integerValue) {
            cell.setCellValue(integerValue.doubleValue());
            cell.setCellStyle(workbookStyles.integerNumberCellStyle());
            return;
        }
        if (value instanceof BigDecimal decimalValue) {
            cell.setCellValue(decimalValue.doubleValue());
            cell.setCellStyle(decimalValue.scale() > 0 ? workbookStyles.decimalNumberCellStyle() : workbookStyles.integerNumberCellStyle());
            return;
        }
        if (value instanceof Number numberValue) {
            // 一般 Number 的處理要放在 BigDecimal / BigInteger 後面，
            // 才能保留較精確型別原本想呈現的格式邏輯。
            cell.setCellValue(numberValue.doubleValue());
            cell.setCellStyle(isIntegralNumber(numberValue) ? workbookStyles.integerNumberCellStyle() : workbookStyles.decimalNumberCellStyle());
            return;
        }
        cell.setCellValue(value.toString());
    }

    private WorkbookStyles createWorkbookStyles(XSSFWorkbook workbook) {
        DataFormat dataFormat = workbook.createDataFormat();

        CellStyle integerStyle = workbook.createCellStyle();
        integerStyle.setDataFormat(dataFormat.getFormat(INTEGER_NUMBER_FORMAT));

        CellStyle decimalStyle = workbook.createCellStyle();
        decimalStyle.setDataFormat(dataFormat.getFormat(DECIMAL_NUMBER_FORMAT));

        return new WorkbookStyles(integerStyle, decimalStyle);
    }

    private boolean isIntegralNumber(Number value) {
        return value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long;
    }

    private void writeCsv(Path csvPath, QueryTable table) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8);
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            writer.write('\uFEFF');
            csvPrinter.printRecord(table.headers());
            for (List<Object> row : table.rows()) {
                List<String> values = row.stream()
                        .map(value -> value == null ? "" : value.toString())
                        .collect(Collectors.toList());
                csvPrinter.printRecord(values);
            }
        }
    }

    private void adjustColumnWidths(Sheet sheet, QueryTable table) {
        for (int columnIndex = 0; columnIndex < table.headers().size(); columnIndex++) {
            int maxLength = table.headers().get(columnIndex).length();
            for (List<Object> row : table.rows()) {
                Object value = columnIndex < row.size() ? row.get(columnIndex) : null;
                if (value != null) {
                    maxLength = Math.max(maxLength, value.toString().length());
                }
            }
            int width = Math.max(10, Math.min(maxLength + 2, 60));
            sheet.setColumnWidth(columnIndex, width * 256);
        }
    }

    private String nextSheetName(String candidate, Set<String> usedSheetNames) {
        String safe = WorkbookUtil.createSafeSheetName(candidate);
        safe = safe.length() > 31 ? safe.substring(0, 31) : safe;
        String resolved = safe;
        int counter = 1;
        // Excel sheet 名稱最多 31 字且必須唯一，因此這裡要同時處理裁切與去重。
        while (!usedSheetNames.add(resolved)) {
            String suffix = "_" + counter++;
            int maxBaseLength = Math.max(1, 31 - suffix.length());
            resolved = safe.substring(0, Math.min(safe.length(), maxBaseLength)) + suffix;
        }
        return resolved;
    }

    private String stripSqlExtension(String filename) {
        return filename.replaceFirst("\\.sql$", "");
    }

    private long successCount(List<ReportFileResult> reportResults) {
        return reportResults.stream().filter(ReportFileResult::success).count();
    }

    private boolean looksLikePivotSql(String sql) {
        return sql.toUpperCase().contains("PIVOT");
    }

    private boolean isEmptyProjectionBinderError(SQLException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("SELECT list is empty after resolving * expressions!");
    }

    private QueryTable noDataTable(Path reportFile) {
        return new QueryTable(
                List.of(NO_DATA_MESSAGE_HEADER),
                List.of(List.of("No data for report " + stripSqlExtension(reportFile.getFileName().toString()) + " under current parameters"))
        );
    }

    private void writeParametersFile(Path parametersFile, ReportGenerationRequest request) {
        try {
            Files.writeString(
                    parametersFile,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to write report parameter file: " + parametersFile, exception);
        }
    }

    private void logExecution(String message) {
        LOGGER.info(message);
        Path logFile = projectPathService.logDir().resolve(REPORT_LOG_FILE);
        String line = "[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] " + message;
        try {
            Files.writeString(
                    logFile,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception exception) {
            LOGGER.warn("Unable to append report log: {}", exception.getMessage());
        }
    }

    private record QueryTable(List<String> headers, List<List<Object>> rows) { }

    /**
     * 僅限單一 workbook 批次內重用的樣式集合。
     */
    private record WorkbookStyles(CellStyle integerNumberCellStyle, CellStyle decimalNumberCellStyle) { }

    public record ReportGenerationResult(
            String timestamp,
            Path runDirectory,
            Path workbookPath,
            Path parametersFile,
            ReportGenerationRequest effectiveRequest,
            List<ReportFileResult> reportResults,
            List<String> failures
    ) { }

    /**
     * 單一報表 SQL 在一個批次中的執行結果。
     */
    public record ReportFileResult(String reportName, int rowCount, boolean success, String errorMessage) { }
}
