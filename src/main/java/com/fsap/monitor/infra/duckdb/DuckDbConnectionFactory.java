package com.fsap.monitor.infra.duckdb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.service.ProjectPathService;

@Component
public class DuckDbConnectionFactory {

    private final ProjectPathService projectPathService;

    public DuckDbConnectionFactory(ProjectPathService projectPathService) {
        this.projectPathService = projectPathService;
    }

    public Connection openConnection() throws SQLException {
        return openConnection(false);
    }

    public Connection openConnection(boolean readOnly) throws SQLException {
        Path dbPath = projectPathService.databaseFile();
        ensureParentDirectory(dbPath);
        // DuckDB JDBC readonly handling is inconsistent across query paths.
        // We enforce readonly at the application layer instead of relying on the driver.
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
