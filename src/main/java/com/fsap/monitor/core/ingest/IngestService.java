package com.fsap.monitor.core.ingest;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.infra.config.FsapProperties;

@Service
public class IngestService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestService.class);
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String INGEST_LOG_FILE = "ingest.log";

    private final ProjectPathService projectPathService;
    private final FsapProperties properties;
    private final ObjectMapper objectMapper;

    public IngestService(ProjectPathService projectPathService, FsapProperties properties, ObjectMapper objectMapper) {
        this.projectPathService = projectPathService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public IngestResult ingest(boolean force, Integer limit, String date) {
        Pattern filenamePattern = Pattern.compile(properties.getIngest().getFilenamePattern());
        List<String> targetSheets = List.copyOf(properties.getIngest().getTargetSheets());
        List<Path> excelFiles = scanExcelFiles(filenamePattern, date);

        if (limit != null && limit > 0 && excelFiles.size() > limit) {
            excelFiles = new ArrayList<>(excelFiles.subList(0, limit));
        }

        try {
            Files.createDirectories(projectPathService.logDir());
            Files.createDirectories(projectPathService.sourceLakeDir());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare ingest directories", exception);
        }

        logIngest("=== FSAP 報表實驗室：Step 1 數據標準化開始 ===");
        logIngest(force ? "🔥 [模式] 強制全量重新轉換 (FORCE MODE)" : "💡 [模式] 增量匯入 (INCREMENTAL MODE)");
        if (date != null && !date.isBlank()) {
            logIngest("🎯 [日期] 僅處理 " + date.trim());
        }
        if (limit != null && limit > 0) {
            logIngest("🎯 [限制] 將處理最新的 " + limit + " 個檔案");
        }

        if (excelFiles.isEmpty()) {
            logIngest("⚠️ 在 " + projectPathService.inputDir() + " 找不到符合條件的 Excel 檔案");
            return new IngestResult(0, 0, 0, List.of());
        }

        int processedFiles = 0;
        int skippedFiles = 0;
        int writtenFiles = 0;
        List<String> failures = new ArrayList<>();

        for (Path excelFile : excelFiles) {
            ProcessResult result = processExcelFile(excelFile, filenamePattern, targetSheets, force);
            processedFiles += result.processed() ? 1 : 0;
            skippedFiles += result.skipped() ? 1 : 0;
            writtenFiles += result.writtenFiles();
            failures.addAll(result.failures());
        }

        logIngest("=== ✨ 數據加工完成！所有成品已存入 02_source_lake ===");
        logIngest("Summary: processed=" + processedFiles + " skipped=" + skippedFiles + " outputs=" + writtenFiles + " failures=" + failures.size());

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Ingest completed with failures");
        }

        return new IngestResult(processedFiles, skippedFiles, writtenFiles, failures);
    }

    private List<Path> scanExcelFiles(Pattern filenamePattern, String exactDate) {
        Path inputDir = projectPathService.inputDir();
        if (!Files.isDirectory(inputDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(inputDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
                    .filter(path -> matchesRequestedDate(path.getFileName().toString(), filenamePattern, exactDate))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to scan input directory: " + inputDir, exception);
        }
    }

    private boolean matchesRequestedDate(String filename, Pattern filenamePattern, String exactDate) {
        Matcher matcher = filenamePattern.matcher(filename);
        if (!matcher.matches()) {
            return false;
        }
        if (exactDate == null || exactDate.isBlank()) {
            return true;
        }
        return exactDate.trim().equals(matcher.group(1));
    }

    private ProcessResult processExcelFile(Path excelFile, Pattern filenamePattern, List<String> targetSheets, boolean force) {
        Matcher matcher = filenamePattern.matcher(excelFile.getFileName().toString());
        if (!matcher.matches()) {
            logIngest("⚠️ 跳過不符命名規則的檔案: " + excelFile.getFileName());
            return new ProcessResult(false, true, 0, List.of());
        }

        String yyyymmdd = matcher.group(1);
        if (!force && allOutputsExist(targetSheets, yyyymmdd)) {
            logIngest("⏭️ [跳過] " + yyyymmdd + " (已存在)");
            return new ProcessResult(false, true, 0, List.of());
        }

        logIngest("🚀 正在加工檔案: " + excelFile.getFileName() + " (日期: " + yyyymmdd + ")");

        int writtenFiles = 0;
        List<String> failures = new ArrayList<>();
        try (InputStream inputStream = Files.newInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Set<String> availableSheets = Stream.iterate(0, index -> index + 1)
                    .limit(workbook.getNumberOfSheets())
                    .map(workbook::getSheetName)
                    .collect(Collectors.toSet());

            for (String sheetName : targetSheets) {
                if (!availableSheets.contains(sheetName)) {
                    continue;
                }

                Path outputFile = projectPathService.sourceLakeDir()
                        .resolve(sheetName)
                        .resolve(sheetName + "-" + yyyymmdd + ".jsonl.gz");

                if (!force && Files.exists(outputFile)) {
                    continue;
                }

                try {
                    Files.createDirectories(outputFile.getParent());
                    int rowCount = writeSheet(workbook.getSheet(sheetName), excelFile.getFileName().toString(), sheetName, yyyymmdd, outputFile);
                    writtenFiles++;
                    logIngest("  ✅ [完成] " + sheetName + " -> " + outputFile.getFileName() + " (共 " + rowCount + " 筆)");
                } catch (Exception exception) {
                    String message = "處理分頁 " + sheetName + " 時發生異常: " + exception.getMessage();
                    failures.add(excelFile.getFileName() + " / " + sheetName + ": " + exception.getMessage());
                    logIngest("  ❌ [錯誤] " + message);
                }
            }
        } catch (Exception exception) {
            String message = "無法讀取 Excel: " + exception.getMessage();
            failures.add(excelFile.getFileName() + ": " + exception.getMessage());
            logIngest("❌ " + message);
        }

        return new ProcessResult(true, false, writtenFiles, failures);
    }

    private boolean allOutputsExist(List<String> targetSheets, String yyyymmdd) {
        for (String sheetName : targetSheets) {
            Path outputFile = projectPathService.sourceLakeDir()
                    .resolve(sheetName)
                    .resolve(sheetName + "-" + yyyymmdd + ".jsonl.gz");
            if (!Files.exists(outputFile)) {
                return false;
            }
        }
        return true;
    }

    private int writeSheet(Sheet sheet, String sourceFilename, String sheetName, String yyyymmdd, Path outputFile) throws Exception {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            return 0;
        }

        List<String> headers = extractHeaders(headerRow);
        int rowCount = 0;
        try (BufferedWriter writer = new BufferedWriter(new java.io.OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)),
                StandardCharsets.UTF_8))) {
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row, headers.size())) {
                    continue;
                }

                Map<String, Object> record = new LinkedHashMap<>();
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    String header = headers.get(columnIndex);
                    record.put(header, normalizeCellValue(row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)));
                }
                record.put("_file", sourceFilename);
                record.put("_sheet", sheetName);
                record.put("_dt", yyyymmdd);
                record.put("_ingest_ts", Instant.now().getEpochSecond());

                writer.write(objectMapper.writeValueAsString(record));
                writer.newLine();
                rowCount++;
            }
        }
        return rowCount;
    }

    private List<String> extractHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();
        short lastCellNum = headerRow.getLastCellNum();
        for (int index = 0; index < lastCellNum; index++) {
            Cell cell = headerRow.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String value = cell == null ? "" : cell.toString().trim();
            headers.add(value.isBlank() ? "column_" + index : value);
        }
        return headers;
    }

    private boolean isBlankRow(Row row, int headerCount) {
        for (int columnIndex = 0; columnIndex < headerCount; columnIndex++) {
            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() != CellType.BLANK && !cell.toString().isBlank()) {
                return false;
            }
        }
        return true;
    }

    private Object normalizeCellValue(Cell cell) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType();
        }

        return switch (type) {
            case STRING -> {
                String value = cell.getStringCellValue();
                yield value == null || value.isBlank() ? null : value;
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> normalizeNumericCell(cell);
            case BLANK -> null;
            default -> {
                String value = cell.toString();
                yield value == null || value.isBlank() ? null : value;
            }
        };
    }

    private Object normalizeNumericCell(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            Date date = cell.getDateCellValue();
            if (date == null) {
                return null;
            }
            Instant instant = date.toInstant();
            LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            boolean hasTimePart = localDateTime.getHour() != 0
                    || localDateTime.getMinute() != 0
                    || localDateTime.getSecond() != 0
                    || localDateTime.getNano() != 0;
            return hasTimePart
                    ? localDateTime.format(ISO_DATE_TIME_FORMAT)
                    : localDateTime.toLocalDate().format(ISO_DATE_FORMAT);
        }

        double numericValue = cell.getNumericCellValue();
        BigDecimal decimal = BigDecimal.valueOf(numericValue).stripTrailingZeros();
        if (decimal.scale() <= 0) {
            return decimal.longValue();
        }
        return decimal.doubleValue();
    }

    private void logIngest(String message) {
        LOGGER.info(message);
        Path logFile = projectPathService.logDir().resolve(INGEST_LOG_FILE);
        String line = "[" + LocalDateTime.now().format(LOG_TIME_FORMAT) + "] " + message;
        try {
            Files.writeString(
                    logFile,
                    line + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception exception) {
            LOGGER.warn("Unable to append ingest log: {}", exception.getMessage());
        }
    }

    private record ProcessResult(boolean processed, boolean skipped, int writtenFiles, List<String> failures) { }

    public record IngestResult(int processedFiles, int skippedFiles, int writtenFiles, List<String> failures) { }
}
