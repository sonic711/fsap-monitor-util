package com.fsap.monitor.core.viewsync;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
/**
 * 依據 {@code 03_sql_logic/views/*.sql} 重新整理 DuckDB views。
 *
 * <p>各 view SQL 之間彼此會互相參照。這裡會先做一輪盡力而為的依賴排序，
 * 然後保留多輪 retry 載入作為保險，處理那些無法單靠靜態分析精準推得的依賴。
 */
public class ViewSyncService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewSyncService.class);

    private final ProjectPathService projectPathService;
    private final DuckDbConnectionFactory connectionFactory;
    private final FsapProperties properties;

    public ViewSyncService(
            ProjectPathService projectPathService,
            DuckDbConnectionFactory connectionFactory,
            FsapProperties properties
    ) {
        this.projectPathService = projectPathService;
        this.connectionFactory = connectionFactory;
        this.properties = properties;
    }

    /**
     * 由 service 自行管理 connection 生命週期的便利版本。
     */
    public ViewSyncResult syncViews(Integer maxRoundsOverride, boolean failFast) {
        try (Connection connection = connectionFactory.openConnection()) {
            return syncViews(connection, maxRoundsOverride, failFast);
        } catch (SQLException exception) {
            return new ViewSyncResult(0, 1, List.of("DuckDB connection failed: " + exception.getMessage()));
        }
    }

    public ViewSyncResult syncViews(Connection connection, Integer maxRoundsOverride, boolean failFast) {
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

        sqlFiles = orderByDependencies(sqlFiles);

        List<String> failures = new ArrayList<>();
        List<Path> pending = new ArrayList<>(sqlFiles);
        Map<Path, String> lastErrors = new LinkedHashMap<>();
        int successCount = 0;
        int configuredRounds = maxRoundsOverride != null ? maxRoundsOverride : properties.getViews().getMaxRounds();
        int maxRounds = Math.max(configuredRounds, sqlFiles.size());

        // 先 drop 再重建，避免 refresh 失敗時還悄悄留下舊版 view 定義，
        // 讓後續報表讀到過期邏輯。
        dropExistingViews(connection, sqlFiles);

        for (int round = 1; round <= maxRounds && !pending.isEmpty(); round++) {
            LOGGER.info("View sync round {} with {} pending files", round, pending.size());
            List<Path> nextPending = new ArrayList<>();
            int roundSuccessCount = 0;

            for (Path sqlFile : pending) {
                try {
                    executeSqlFile(connection, sqlFile);
                    successCount++;
                    roundSuccessCount++;
                    lastErrors.remove(sqlFile);
                    LOGGER.info("View ready: {}", sqlFile.getFileName());
                } catch (Exception exception) {
                    LOGGER.warn("View load failed for {}: {}", sqlFile.getFileName(), exception.getMessage());
                    lastErrors.put(sqlFile, exception.getMessage());
                    if (failFast) {
                        failures.add(sqlFile.getFileName() + ": " + exception.getMessage());
                        return new ViewSyncResult(successCount, failures.size(), failures);
                    }
                    nextPending.add(sqlFile);
                }
            }

            if (nextPending.isEmpty()) {
                pending = nextPending;
                break;
            }

            if (roundSuccessCount == 0) {
                // 若某一輪完全沒有進度，代表剩下的檔案已被壞 SQL、缺依賴或循環依賴卡死，
                // 再重試也只會得到同樣結果。
                LOGGER.warn("View sync stopped after round {} because no pending views could be resolved", round);
                for (Path sqlFile : nextPending) {
                    String message = lastErrors.getOrDefault(sqlFile, "Unknown error");
                    failures.add(sqlFile.getFileName() + ": " + message);
                }
                return new ViewSyncResult(successCount, failures.size(), failures);
            }

            pending = nextPending;
        }

        if (!pending.isEmpty()) {
            for (Path sqlFile : pending) {
                String message = lastErrors.getOrDefault(sqlFile, "View remained pending after max retry rounds");
                failures.add(sqlFile.getFileName() + ": " + message);
            }
        }

        return new ViewSyncResult(successCount, failures.size(), failures);
    }

    private void executeSqlFile(Connection connection, Path sqlFile) throws Exception {
        String sql = projectPathService.rewriteProjectRelativePaths(Files.readString(sqlFile));
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private List<Path> orderByDependencies(List<Path> sqlFiles) {
        Map<String, Path> viewFilesByName = sqlFiles.stream()
                .collect(Collectors.toMap(
                        this::viewName,
                        path -> path,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Path, Set<Path>> dependenciesByFile = new LinkedHashMap<>();
        Map<Path, Integer> indegree = new LinkedHashMap<>();
        Map<Path, List<Path>> dependents = new HashMap<>();

        for (Path sqlFile : sqlFiles) {
            Set<Path> dependencies = discoverDependencies(sqlFile, viewFilesByName);
            dependenciesByFile.put(sqlFile, dependencies);
            indegree.put(sqlFile, dependencies.size());
            for (Path dependency : dependencies) {
                dependents.computeIfAbsent(dependency, ignored -> new ArrayList<>()).add(sqlFile);
            }
        }

        PriorityQueue<Path> ready = new PriorityQueue<>(Comparator.comparing(path -> path.getFileName().toString()));
        for (Map.Entry<Path, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
            }
        }

        List<Path> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            Path current = ready.poll();
            ordered.add(current);
            for (Path dependent : dependents.getOrDefault(current, List.of())) {
                int nextValue = indegree.computeIfPresent(dependent, (ignored, value) -> value - 1);
                if (nextValue == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (ordered.size() == sqlFiles.size()) {
            return ordered;
        }

        // 靜態依賴分析故意做得較輕量；排不準的部分交給後面的 retry rounds 收斂。
        List<Path> unresolved = sqlFiles.stream()
                .filter(path -> !ordered.contains(path))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .collect(Collectors.toList());
        LOGGER.warn("View dependency ordering left {} files unresolved; falling back to retry ordering for those files", unresolved.size());
        ordered.addAll(unresolved);
        return ordered;
    }

    private Set<Path> discoverDependencies(Path sqlFile, Map<String, Path> viewFilesByName) {
        String currentViewName = viewName(sqlFile);
        String sqlContent;
        try {
            sqlContent = Files.readString(sqlFile).toLowerCase();
        } catch (Exception exception) {
            LOGGER.warn("Unable to inspect dependencies for {}: {}", sqlFile.getFileName(), exception.getMessage());
            return Set.of();
        }

        Set<Path> dependencies = new HashSet<>();
        for (Map.Entry<String, Path> candidate : viewFilesByName.entrySet()) {
            if (candidate.getKey().equals(currentViewName)) {
                continue;
            }
            // 用 word boundary 避免把 v_rt_cnt 誤判成命中較長的識別字，
            // 例如 v_rt_cnt_daily。
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(candidate.getKey()) + "\\b");
            if (pattern.matcher(sqlContent).find()) {
                dependencies.add(candidate.getValue());
            }
        }
        return dependencies;
    }

    private String viewName(Path sqlFile) {
        return sqlFile.getFileName().toString().replaceFirst("\\.sql$", "").toLowerCase();
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

    /**
     * view refresh 結束後回傳給呼叫端的總結結果。
     */
    public record ViewSyncResult(int successCount, int failureCount, List<String> failures) { }
}
