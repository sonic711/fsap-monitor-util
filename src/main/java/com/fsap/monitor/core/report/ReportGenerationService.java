package com.fsap.monitor.core.report;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.viewsync.ViewSyncService;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
public class ReportGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGenerationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final String REPORT_LOG_FILE = "report_execution.log";
    private static final String WORKBOOK_PREFIX = "維運月度報表_彙總_";

    private final ProjectPathService projectPathService;
    private final DuckDbConnectionFactory connectionFactory;
    private final ViewSyncService viewSyncService;

    public ReportGenerationService(
            ProjectPathService projectPathService,
            DuckDbConnectionFactory connectionFactory,
            ViewSyncService viewSyncService
    ) {
        this.projectPathService = projectPathService;
        this.connectionFactory = connectionFactory;
        this.viewSyncService = viewSyncService;
    }

    public ReportGenerationResult generate(String timestampOverride, boolean continueOnError) {
        String timestamp = timestampOverride != null && !timestampOverride.isBlank()
                ? timestampOverride.trim()
                : LocalDateTime.now().format(TIMESTAMP_FORMAT);

        Path runDirectory = projectPathService.reportOutputDir().resolve(timestamp);
        Path workbookPath = runDirectory.resolve(WORKBOOK_PREFIX + timestamp + ".xlsx");

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

        List<Path> reportFiles = scanReportFiles();
        List<String> failures = new ArrayList<>();
        List<ReportFileResult> reportResults = new ArrayList<>();
        Throwable fatalFailure = null;

        try (Connection connection = connectionFactory.openConnection();
             XSSFWorkbook workbook = new XSSFWorkbook()) {
            logExecution("✅ [資料庫] 已連接實體庫: " + projectPathService.databaseFile().getFileName());

            loadMacros(connection);
            syncViews(connection);

            Set<String> usedSheetNames = new HashSet<>();
            for (Path reportFile : reportFiles) {
                String baseName = stripSqlExtension(reportFile.getFileName().toString());
                String sheetName = nextSheetName(baseName, usedSheetNames);
                logExecution("📝 [處理中] " + reportFile.getFileName());

                try {
                    QueryTable table = executeReportQuery(connection, reportFile);
                    writeWorkbookSheet(workbook, sheetName, table);
                    writeCsv(runDirectory.resolve(baseName + ".csv"), table);
                    reportResults.add(new ReportFileResult(baseName, table.rows().size(), true, null));
                    logExecution("  ✅ 完成 (" + table.rows().size() + " 筆)");
                } catch (Exception exception) {
                    String errorMessage = reportFile.getFileName() + ": " + exception.getMessage();
                    failures.add(errorMessage);
                    reportResults.add(new ReportFileResult(baseName, 0, false, exception.getMessage()));
                    writeErrorSheet(workbook, nextSheetName("ERR_" + baseName, usedSheetNames), exception.getMessage());
                    logExecution("  ❌ 失敗: " + exception.getMessage());
                    if (!continueOnError) {
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

        return new ReportGenerationResult(timestamp, runDirectory, workbookPath, reportResults, failures);
    }

    private List<Path> scanReportFiles() {
        Path reportsDir = projectPathService.reportsDir();
        if (!Files.isDirectory(reportsDir)) {
            throw new IllegalStateException("Reports directory not found: " + reportsDir);
        }

        try (Stream<Path> stream = Files.list(reportsDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to scan reports directory: " + reportsDir, exception);
        }
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

    private QueryTable executeReportQuery(Connection connection, Path reportFile) throws Exception {
        String sql = projectPathService.rewriteProjectRelativePaths(Files.readString(reportFile));
        try (Statement statement = connection.createStatement()) {
            boolean hasResultSet = statement.execute(sql);
            if (!hasResultSet) {
                throw new IllegalStateException("SQL did not return a result set");
            }

            try (ResultSet resultSet = statement.getResultSet()) {
                return readResultSet(resultSet);
            }
        }
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

    private void writeWorkbookSheet(XSSFWorkbook workbook, String sheetName, QueryTable table) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row headerRow = sheet.createRow(0);
        for (int index = 0; index < table.headers().size(); index++) {
            headerRow.createCell(index).setCellValue(table.headers().get(index));
        }

        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            List<Object> values = table.rows().get(rowIndex);
            for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                writeCellValue(row.createCell(columnIndex), values.get(columnIndex));
            }
        }

        adjustColumnWidths(sheet, table);
    }

    private void writeErrorSheet(XSSFWorkbook workbook, String sheetName, String message) {
        QueryTable errorTable = new QueryTable(List.of("Error"), List.of(List.of(message)));
        writeWorkbookSheet(workbook, sheetName, errorTable);
    }

    private void writeCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Boolean booleanValue) {
            cell.setCellValue(booleanValue);
            return;
        }
        if (value instanceof Integer integerValue) {
            cell.setCellValue(integerValue.doubleValue());
            return;
        }
        if (value instanceof Long longValue) {
            cell.setCellValue(longValue.doubleValue());
            return;
        }
        if (value instanceof Double doubleValue) {
            cell.setCellValue(doubleValue);
            return;
        }
        if (value instanceof Float floatValue) {
            cell.setCellValue(floatValue.doubleValue());
            return;
        }
        if (value instanceof BigDecimal decimalValue) {
            cell.setCellValue(decimalValue.doubleValue());
            return;
        }
        cell.setCellValue(value.toString());
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

    public record ReportGenerationResult(
            String timestamp,
            Path runDirectory,
            Path workbookPath,
            List<ReportFileResult> reportResults,
            List<String> failures
    ) { }

    public record ReportFileResult(String reportName, int rowCount, boolean success, String errorMessage) { }
}
