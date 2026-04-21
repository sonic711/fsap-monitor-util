package com.fsap.monitor.core.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

@Service
public class EnvironmentCheckService {

    private final ProjectPathService projectPathService;
    private final DuckDbConnectionFactory connectionFactory;

    public EnvironmentCheckService(ProjectPathService projectPathService, DuckDbConnectionFactory connectionFactory) {
        this.projectPathService = projectPathService;
        this.connectionFactory = connectionFactory;
    }

    public DoctorReport runChecks() {
        List<CheckResult> checks = new ArrayList<>();
        checks.add(checkDirectory("Base directory", projectPathService.baseDir(), true));
        checks.add(checkDirectory("Input directory", projectPathService.inputDir(), true));
        checks.add(checkDirectory("Views directory", projectPathService.viewsDir(), true));
        checks.add(checkDirectory("Reports directory", projectPathService.reportsDir(), true));
        checks.add(checkWritableParent("Database parent", projectPathService.databaseFile().getParent()));
        checks.add(checkWritableDirectory("Log directory", projectPathService.logDir()));
        checks.add(checkDuckDbConnection());
        boolean healthy = checks.stream().allMatch(CheckResult::ok);
        return new DoctorReport(healthy, checks);
    }

    private CheckResult checkDirectory(String name, Path path, boolean required) {
        boolean exists = path != null && Files.exists(path) && Files.isDirectory(path);
        if (exists) {
            return new CheckResult(name, true, path.toString(), "OK");
        }
        String message = required ? "Missing required directory" : "Directory not found";
        return new CheckResult(name, !required, path.toString(), message);
    }

    private CheckResult checkWritableParent(String name, Path path) {
        if (path == null) {
            return new CheckResult(name, false, "", "Parent path is null");
        }
        try {
            Files.createDirectories(path);
            boolean writable = Files.isWritable(path);
            return new CheckResult(name, writable, path.toString(), writable ? "OK" : "Parent directory not writable");
        } catch (Exception exception) {
            return new CheckResult(name, false, path.toString(), exception.getMessage());
        }
    }

    private CheckResult checkWritableDirectory(String name, Path path) {
        try {
            Files.createDirectories(path);
            return new CheckResult(name, Files.isWritable(path), path.toString(), Files.isWritable(path) ? "OK" : "Directory not writable");
        } catch (Exception exception) {
            return new CheckResult(name, false, path.toString(), exception.getMessage());
        }
    }

    private CheckResult checkDuckDbConnection() {
        try (Connection ignored = connectionFactory.openConnection()) {
            return new CheckResult("DuckDB connection", true, projectPathService.databaseFile().toString(), "OK");
        } catch (Exception exception) {
            return new CheckResult("DuckDB connection", false, projectPathService.databaseFile().toString(), exception.getMessage());
        }
    }

    public record DoctorReport(boolean healthy, List<CheckResult> checks) { }

    public record CheckResult(String name, boolean ok, String target, String message) { }
}
