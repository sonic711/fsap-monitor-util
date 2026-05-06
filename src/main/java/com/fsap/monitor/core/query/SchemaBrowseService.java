package com.fsap.monitor.core.query;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

/**
 * 讀取給 UI sidebar 顯示的輕量 schema 快照。
 */
@Service
public class SchemaBrowseService {

    private final DuckDbConnectionFactory connectionFactory;

    public SchemaBrowseService(DuckDbConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public List<Map<String, Object>> loadSchemaSnapshot() throws Exception {
        String sql = """
                SELECT table_name, table_type
                FROM information_schema.tables
                WHERE table_schema = 'main'
                ORDER BY table_type, table_name
                """;
        try (Connection connection = connectionFactory.openConnection(true);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table_name", resultSet.getString("table_name"));
                row.put("table_type", resultSet.getString("table_type"));
                rows.add(row);
            }
            return rows;
        }
    }
}
