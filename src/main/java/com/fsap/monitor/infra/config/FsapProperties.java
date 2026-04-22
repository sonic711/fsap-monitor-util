package com.fsap.monitor.infra.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fsap")
public class FsapProperties {

    private final Paths paths = new Paths();
    private final Ingest ingest = new Ingest();
    private final Views views = new Views();
    private final Web web = new Web();
    private final Monitor monitor = new Monitor();

    public Paths getPaths() {
        return paths;
    }

    public Ingest getIngest() {
        return ingest;
    }

    public Views getViews() {
        return views;
    }

    public Web getWeb() {
        return web;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public static class Paths {
        private String baseDir = ".";
        private String inputDir = "01_excel_input";
        private String sourceLakeDir = "02_source_lake";
        private String sqlLogicDir = "03_sql_logic";
        private String reportOutputDir = "04_report_output";
        private String databaseFile = "05_database/fsap-month-report.duckdb";
        private String logDir = "logs";

        public String getBaseDir() {
            return baseDir;
        }

        public void setBaseDir(String baseDir) {
            this.baseDir = baseDir;
        }

        public String getInputDir() {
            return inputDir;
        }

        public void setInputDir(String inputDir) {
            this.inputDir = inputDir;
        }

        public String getSourceLakeDir() {
            return sourceLakeDir;
        }

        public void setSourceLakeDir(String sourceLakeDir) {
            this.sourceLakeDir = sourceLakeDir;
        }

        public String getSqlLogicDir() {
            return sqlLogicDir;
        }

        public void setSqlLogicDir(String sqlLogicDir) {
            this.sqlLogicDir = sqlLogicDir;
        }

        public String getReportOutputDir() {
            return reportOutputDir;
        }

        public void setReportOutputDir(String reportOutputDir) {
            this.reportOutputDir = reportOutputDir;
        }

        public String getDatabaseFile() {
            return databaseFile;
        }

        public void setDatabaseFile(String databaseFile) {
            this.databaseFile = databaseFile;
        }

        public String getLogDir() {
            return logDir;
        }

        public void setLogDir(String logDir) {
            this.logDir = logDir;
        }
    }

    public static class Ingest {
        private List<String> targetSheets = new ArrayList<>(List.of(
                "RT_CNT",
                "RT_TMSPT",
                "RT_PR_HH24",
                "RT_NODE_HH24",
                "BT_CNT",
                "MON_LOG",
                "ERR_LOG"
        ));
        private String filenamePattern = "FSAP每日交易統計(\\d{8})\\.xlsx";

        public List<String> getTargetSheets() {
            return targetSheets;
        }

        public void setTargetSheets(List<String> targetSheets) {
            this.targetSheets = targetSheets;
        }

        public String getFilenamePattern() {
            return filenamePattern;
        }

        public void setFilenamePattern(String filenamePattern) {
            this.filenamePattern = filenamePattern;
        }
    }

    public static class Views {
        private int maxRounds = 3;

        public int getMaxRounds() {
            return maxRounds;
        }

        public void setMaxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
        }
    }

    public static class Web {
        private boolean readonly = false;

        public boolean isReadonly() {
            return readonly;
        }

        public void setReadonly(boolean readonly) {
            this.readonly = readonly;
        }
    }

    public static class Monitor {
        private String configFile = "config/monitor-data.json";

        public String getConfigFile() {
            return configFile;
        }

        public void setConfigFile(String configFile) {
            this.configFile = configFile;
        }
    }
}
