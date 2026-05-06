package com.fsap.monitor.core.query;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
/**
 * 執行 UI query console 發出的 ad-hoc SQL。
 *
 * <p>在 readonly 模式下，這裡會先攔掉修改型 SQL，再交給 DuckDB。
 * 原因是 JDBC driver 的 readonly 行為不能被完全信任。
 */
public class QueryService {

    private static final List<String> READONLY_PREFIXES = List.of(
            "SELECT",
            "WITH",
            "SHOW",
            "DESCRIBE",
            "PRAGMA",
            "EXPLAIN"
    );

    private final DuckDbConnectionFactory connectionFactory;
    private final FsapProperties properties;

    public QueryService(DuckDbConnectionFactory connectionFactory, FsapProperties properties) {
        this.connectionFactory = connectionFactory;
        this.properties = properties;
    }

    /**
     * 驗證並執行 SQL，最後把完整結果集實體化成可序列化的表格結構回給 web 層。
     */
    public QueryResult execute(String sql) throws Exception {
        validateQuery(sql);
        try (Connection connection = connectionFactory.openConnection(properties.getWeb().isReadonly());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return readResult(resultSet);
        }
    }

    private void validateQuery(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL must not be blank");
        }
        if (!properties.getWeb().isReadonly()) {
            return;
        }
        // readonly 模式故意採用 prefix-based 白名單，因為這個 query console 的目的
        // 是查詢與檢視，不是提供任意 SQL 腳本執行能力。
        String normalized = sql.stripLeading().toUpperCase(Locale.ROOT);
        boolean allowed = READONLY_PREFIXES.stream().anyMatch(normalized::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("Readonly mode only allows query statements");
        }
    }

    private QueryResult readResult(ResultSet resultSet) throws Exception {
        ResultSetMetaData metadata = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int index = 1; index <= metadata.getColumnCount(); index++) {
            columns.add(metadata.getColumnLabel(index));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int index = 1; index <= metadata.getColumnCount(); index++) {
                row.put(metadata.getColumnLabel(index), resultSet.getObject(index));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows);
    }

    /**
     * 方便序列化成 JSON 的查詢結果表示法。
     */
    public record QueryResult(List<String> columns, List<Map<String, Object>> rows) { }
}
