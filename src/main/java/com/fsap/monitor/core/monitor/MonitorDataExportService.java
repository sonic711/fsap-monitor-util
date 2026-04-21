package com.fsap.monitor.core.monitor;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.viewsync.ViewSyncService;
import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
public class MonitorDataExportService {

    private final ObjectMapper objectMapper;
    private final ProjectPathService projectPathService;
    private final DuckDbConnectionFactory connectionFactory;
    private final ViewSyncService viewSyncService;
    private final FsapProperties properties;

    public MonitorDataExportService(
            ObjectMapper objectMapper,
            ProjectPathService projectPathService,
            DuckDbConnectionFactory connectionFactory,
            ViewSyncService viewSyncService,
            FsapProperties properties
    ) {
        this.objectMapper = objectMapper;
        this.projectPathService = projectPathService;
        this.connectionFactory = connectionFactory;
        this.viewSyncService = viewSyncService;
        this.properties = properties;
    }

    public MonitorExportResult export(String configPathOverride) {
        Path configPath = resolveConfigPath(configPathOverride);

        MonitorConfig config = loadConfig(configPath);
        Path outputDirectory = projectPathService.resolve(config.outputDir());

        try {
            Files.createDirectories(outputDirectory);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create monitor output directory: " + outputDirectory, exception);
        }

        List<String> failures = new ArrayList<>();
        List<TaskResult> taskResults = new ArrayList<>();

        try (Connection connection = connectionFactory.openConnection()) {
            ViewSyncService.ViewSyncResult syncResult = viewSyncService.syncViews(connection, null, false);
            if (syncResult.failureCount() > 0) {
                throw new IllegalStateException("View sync finished with failures");
            }

            for (MonitorTask task : config.tasks()) {
                String sql = generateSql(task.view(), task.type());
                QueryTable table = executeQuery(connection, sql);
                if (table.rows().isEmpty()) {
                    taskResults.add(new TaskResult(task.view(), task.filename(), 0, true, true, null));
                    continue;
                }

                writeOutputs(outputDirectory, task.filename(), table);
                taskResults.add(new TaskResult(task.view(), task.filename(), table.rows().size(), true, false, null));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Monitor data export failed", exception);
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Monitor data export completed with failures");
        }

        return new MonitorExportResult(configPath, outputDirectory, taskResults, failures);
    }

    private MonitorConfig loadConfig(Path configPath) {
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("Monitor config not found: " + configPath);
        }
        try {
            MonitorConfig config = objectMapper.readValue(Files.readString(configPath), MonitorConfig.class);
            if (config.tasks() == null || config.tasks().isEmpty()) {
                throw new IllegalStateException("Monitor config contains no tasks");
            }
            return config;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load monitor config: " + configPath, exception);
        }
    }

    private Path resolveConfigPath(String configPathOverride) {
        String configuredValue = configPathOverride == null || configPathOverride.isBlank()
                ? properties.getMonitor().getConfigFile()
                : configPathOverride.trim();

        Path rawPath = Path.of(configuredValue);
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }

        Path baseRelative = projectPathService.resolve(configuredValue);
        if (Files.exists(baseRelative)) {
            return baseRelative;
        }

        return Path.of(System.getProperty("user.dir")).resolve(rawPath).normalize();
    }

    private String generateSql(String viewName, String taskType) {
        if ("daily".equalsIgnoreCase(taskType)) {
            return """
                    SELECT *
                    FROM %s
                    WHERE CAST(log_date AS TIMESTAMP) >= (
                        SELECT MAX(CAST(log_date AS TIMESTAMP)) FROM %s
                    ) - INTERVAL 10 DAY
                    ORDER BY application, TARGET_IP, log_date DESC
                    LIMIT 10000
                    """.formatted(viewName, viewName);
        }
        if ("hourly".equalsIgnoreCase(taskType)) {
            return """
                    SELECT *
                    FROM %s
                    WHERE CAST(log_date AS TIMESTAMP) >= (
                        SELECT MAX(CAST(log_date AS TIMESTAMP)) FROM %s
                    ) - INTERVAL 4 DAY
                    ORDER BY application, TARGET_IP, log_date DESC, log_hour DESC
                    LIMIT 10000
                    """.formatted(viewName, viewName);
        }
        throw new IllegalArgumentException("Unsupported monitor task type: " + taskType);
    }

    private QueryTable executeQuery(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            List<String> headers = new ArrayList<>();
            for (int columnIndex = 1; columnIndex <= metadata.getColumnCount(); columnIndex++) {
                headers.add(metadata.getColumnLabel(columnIndex));
            }

            List<List<Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                List<Object> row = new ArrayList<>(headers.size());
                for (int columnIndex = 1; columnIndex <= metadata.getColumnCount(); columnIndex++) {
                    row.add(resultSet.getObject(columnIndex));
                }
                rows.add(row);
            }
            return new QueryTable(headers, rows);
        }
    }

    private void writeOutputs(Path outputDirectory, String filename, QueryTable table) throws Exception {
        String csvContent = toCsv(table);
        Path csvPath = outputDirectory.resolve(filename + ".csv");
        Path jsPath = outputDirectory.resolve(filename + ".js");

        Files.writeString(
                csvPath,
                "\uFEFF" + csvContent,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        String jsContent = "const csv_" + filename + " = `\n" + csvContent + "\n`;";
        Files.writeString(
                jsPath,
                jsContent,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    private String toCsv(QueryTable table) throws Exception {
        StringWriter stringWriter = new StringWriter();
        try (CSVPrinter csvPrinter = new CSVPrinter(stringWriter, CSVFormat.DEFAULT)) {
            csvPrinter.printRecord(table.headers());
            for (List<Object> row : table.rows()) {
                csvPrinter.printRecord(row.stream()
                        .map(value -> value == null ? "" : value.toString())
                        .collect(Collectors.toList()));
            }
        }
        return stringWriter.toString();
    }

    private record QueryTable(List<String> headers, List<List<Object>> rows) { }

    public record MonitorExportResult(Path configPath, Path outputDirectory, List<TaskResult> taskResults, List<String> failures) { }

    public record TaskResult(String viewName, String filename, int rowCount, boolean success, boolean empty, String errorMessage) { }

    public record MonitorConfig(
            @JsonProperty("db_path") String dbPath,
            @JsonProperty("output_dir") String outputDir,
            List<MonitorTask> tasks
    ) { }

    public record MonitorTask(String view, String filename, String type) { }
}
