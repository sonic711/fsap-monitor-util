package com.fsap.monitor.core.query;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;

/**
 * 以 JSONL 形式保存近期 ad-hoc query 歷程，供 web UI 顯示。
 */
@Service
public class QueryHistoryService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String HISTORY_FILE = "query_history.log.jsonl";

    private final ProjectPathService projectPathService;
    private final ObjectMapper objectMapper;

    public QueryHistoryService(ProjectPathService projectPathService, ObjectMapper objectMapper) {
        this.projectPathService = projectPathService;
        this.objectMapper = objectMapper;
    }

    public void record(String sql, int resultCount) {
        try {
            Files.createDirectories(projectPathService.logDir());
            HistoryEntry entry = new HistoryEntry(
                    LocalDateTime.now().format(TIME_FORMAT),
                    sql,
                    summarize(sql),
                    resultCount
            );
            Path historyFile = projectPathService.logDir().resolve(HISTORY_FILE);
            Files.writeString(
                    historyFile,
                    objectMapper.writeValueAsString(entry) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
            // query history 失敗不能反過來阻擋實際查詢。
        }
    }

    public List<HistoryEntry> loadRecent(int limit) {
        Path historyFile = projectPathService.logDir().resolve(HISTORY_FILE);
        if (!Files.isRegularFile(historyFile)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(historyFile, StandardCharsets.UTF_8);
            List<HistoryEntry> entries = new ArrayList<>();
            for (int index = lines.size() - 1; index >= 0 && entries.size() < limit; index--) {
                String line = lines.get(index).trim();
                if (line.isBlank()) {
                    continue;
                }
                entries.add(objectMapper.readValue(line, HistoryEntry.class));
            }
            return entries;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String summarize(String sql) {
        if (sql == null) {
            return "";
        }
        String normalized = sql.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    public record HistoryEntry(
            String timestamp,
            String sql,
            String title,
            @JsonProperty("result_count") int resultCount
    ) { }
}
