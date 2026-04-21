package com.fsap.monitor.core.service;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fsap.monitor.infra.config.FsapProperties;

@Service
public class ProjectPathService {

    private static final Pattern PROJECT_RELATIVE_PATH_PATTERN = Pattern.compile(
            "'(00_info/[^']*|01_excel_input/[^']*|02_source_lake/[^']*|03_sql_logic/[^']*|04_report_output/[^']*|05_database/[^']*|logs/[^']*)'"
    );

    private final FsapProperties properties;

    public ProjectPathService(FsapProperties properties) {
        this.properties = properties;
    }

    public Path baseDir() {
        return Path.of(properties.getPaths().getBaseDir()).toAbsolutePath().normalize();
    }

    public Path inputDir() {
        return resolve(properties.getPaths().getInputDir());
    }

    public Path sourceLakeDir() {
        return resolve(properties.getPaths().getSourceLakeDir());
    }

    public Path sqlLogicDir() {
        return resolve(properties.getPaths().getSqlLogicDir());
    }

    public Path viewsDir() {
        return sqlLogicDir().resolve("views");
    }

    public Path macrosDir() {
        return sqlLogicDir().resolve("macros");
    }

    public Path reportsDir() {
        return sqlLogicDir().resolve("reports");
    }

    public Path reportOutputDir() {
        return resolve(properties.getPaths().getReportOutputDir());
    }

    public Path databaseFile() {
        return resolve(properties.getPaths().getDatabaseFile());
    }

    public Path logDir() {
        return resolve(properties.getPaths().getLogDir());
    }

    public Path resolve(String value) {
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return baseDir().resolve(path).normalize();
    }

    public String rewriteProjectRelativePaths(String sql) {
        Matcher matcher = PROJECT_RELATIVE_PATH_PATTERN.matcher(sql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rewritten = "'" + resolve(matcher.group(1)).toString().replace("\\", "/") + "'";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(rewritten));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
