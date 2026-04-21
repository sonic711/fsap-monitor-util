package com.fsap.monitor.core.viewsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
public class ViewSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewSyncService.class);

    private final ProjectPathService projectPathService;
    private final DuckDbConnectionFactory connectionFactory;

    public ViewSyncService(ProjectPathService projectPathService, DuckDbConnectionFactory connectionFactory) {
        this.projectPathService = projectPathService;
        this.connectionFactory = connectionFactory;
    }

    public ViewSyncResult syncViews(Integer maxRoundsOverride, boolean failFast) {
        try (Connection connection = connectionFactory.openConnection()) {
            return syncViews(connection, maxRoundsOverride, failFast);
        } catch (SQLException exception) {
            return new ViewSyncResult(0, 1, List.of("DuckDB connection failed: " + exception.getMessage()));
        }
    }

    public ViewSyncResult syncViews(Connection connection, Integer maxRoundsOverride, boolean failFast) {
        int maxRounds = maxRoundsOverride != null ? maxRoundsOverride : 3;
        Path viewsDir = projectPathService.viewsDir();
        if (!Files.isDirectory(viewsDir)) {
            return new ViewSyncResult(0, 0, List.of("Views directory not found: " + viewsDir));
        }

        List<Path> sqlFiles;
        try (Stream<Path> stream = Files.list(viewsDir)) {
            sqlFiles = stream
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            return new ViewSyncResult(0, 0, List.of("Unable to scan views directory: " + exception.getMessage()));
        }

        List<String> failures = new ArrayList<>();
        List<Path> pending = new ArrayList<>(sqlFiles);
        int successCount = 0;

        dropExistingViews(connection, sqlFiles);

        for (int round = 1; round <= maxRounds && !pending.isEmpty(); round++) {
            LOGGER.info("View sync round {} with {} pending files", round, pending.size());
            List<Path> nextPending = new ArrayList<>();

            for (Path sqlFile : pending) {
                try {
                    executeSqlFile(connection, sqlFile);
                    successCount++;
                    LOGGER.info("View ready: {}", sqlFile.getFileName());
                } catch (Exception exception) {
                    LOGGER.warn("View load failed for {}: {}", sqlFile.getFileName(), exception.getMessage());
                    if (failFast) {
                        failures.add(sqlFile.getFileName() + ": " + exception.getMessage());
                        return new ViewSyncResult(successCount, failures.size(), failures);
                    }
                    if (round == maxRounds) {
                        failures.add(sqlFile.getFileName() + ": " + exception.getMessage());
                    } else {
                        nextPending.add(sqlFile);
                    }
                }
            }

            pending = nextPending;
        }

        return new ViewSyncResult(successCount, failures.size(), failures);
    }

    private void executeSqlFile(Connection connection, Path sqlFile) throws Exception {
        String sql = projectPathService.rewriteProjectRelativePaths(Files.readString(sqlFile));
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void dropExistingViews(Connection connection, List<Path> sqlFiles) {
        for (Path sqlFile : sqlFiles) {
            String viewName = sqlFile.getFileName().toString().replaceFirst("\\.sql$", "");
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP VIEW IF EXISTS " + viewName);
                LOGGER.info("Dropped existing view if present: {}", viewName);
            } catch (Exception exception) {
                LOGGER.warn("Failed to drop existing view {}: {}", viewName, exception.getMessage());
            }
        }
    }

    public record ViewSyncResult(int successCount, int failureCount, List<String> failures) { }
}
