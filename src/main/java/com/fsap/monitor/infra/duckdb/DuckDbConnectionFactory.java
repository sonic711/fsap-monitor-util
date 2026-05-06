package com.fsap.monitor.infra.duckdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.service.ProjectPathService;

@Component
/**
 * 建立連往專案 DuckDB 檔案的 JDBC 連線。
 *
 * <p>同時確保資料庫父目錄在 driver 開檔前已存在，避免每個呼叫端都重複處理
 * 第一次啟動的目錄建立邏輯。
 */
public class DuckDbConnectionFactory {

    private final ProjectPathService projectPathService;

    public DuckDbConnectionFactory(ProjectPathService projectPathService) {
        this.projectPathService = projectPathService;
    }

    public Connection openConnection() throws SQLException {
        return openConnection(false);
    }

    /**
     * 開啟 JDBC 連線。
     *
     * <p>{@code readOnly} 目前只是提示性參數。DuckDB JDBC 對 readonly 的實際
     * 行為並不一致，所以真正的唯讀限制仍由應用層負責。
     */
    public Connection openConnection(boolean readOnly) throws SQLException {
        Path dbPath = projectPathService.databaseFile();
        ensureParentDirectory(dbPath);
        // DuckDB JDBC 在不同查詢路徑上的 readonly 行為不一致，因此不能單靠 driver。
        return DriverManager.getConnection("jdbc:duckdb:" + dbPath);
    }

    private void ensureParentDirectory(Path dbPath) throws SQLException {
        Path parent = dbPath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception exception) {
            throw new SQLException("Unable to create DuckDB parent directory: " + parent, exception);
        }
    }
}
