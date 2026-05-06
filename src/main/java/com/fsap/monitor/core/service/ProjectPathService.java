package com.fsap.monitor.core.service;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fsap.monitor.infra.config.FsapProperties;

@Service
/**
 * 集中管理整個應用程式使用的路徑解析規則。
 *
 * <p>這個專案同時存在三種路徑型態：
 * 1. {@link FsapProperties} 中設定的路徑
 * 2. 執行時由 base directory 解析出的絕對路徑
 * 3. SQL 文字中仍沿用的專案相對路徑
 *
 * <p>把轉換規則集中在這裡，可以避免 ingest、report、view sync、query
 * 各自重複實作 path join 與重寫邏輯。
 */
public class ProjectPathService {

    private static final Pattern PROJECT_RELATIVE_PATH_PATTERN = Pattern.compile(
            "'(00_info/[^']*|01_excel_input/[^']*|02_source_lake/[^']*|03_sql_logic/[^']*|04_report_output/[^']*|05_database/[^']*|logs/[^']*)'"
    );

    private final FsapProperties properties;

    public ProjectPathService(FsapProperties properties) {
        this.properties = properties;
    }

    /**
     * 回傳包含 00~05 專案目錄的根路徑。
     */
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

    /**
     * 把 SQL 中仍使用專案相對路徑的字串常值改寫成絕對路徑，
     * 讓 DuckDB 不受目前 working directory 影響也能正確讀檔。
     */
    public String rewriteProjectRelativePaths(String sql) {
        Matcher matcher = PROJECT_RELATIVE_PATH_PATTERN.matcher(sql);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String rewritten = "'" + resolveSqlLiteralPath(matcher.group(1)) + "'";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(rewritten));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolveSqlLiteralPath(String value) {
        // glob pattern 必須保留成字串，不能直接走 Path normalization，
        // 否則 DuckDB 讀檔時需要的 wildcard 語意會被破壞。
        if (containsGlobPattern(value)) {
            String base = baseDir().toString().replace("\\", "/");
            String relative = value.replace("\\", "/");
            if (base.endsWith("/")) {
                return base + relative;
            }
            return base + "/" + relative;
        }
        return resolve(value).toString().replace("\\", "/");
    }

    private boolean containsGlobPattern(String value) {
        return value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf('[') >= 0;
    }
}
