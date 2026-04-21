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

    public record QueryResult(List<String> columns, List<Map<String, Object>> rows) { }
}
