package com.fsap.monitor.infra.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fsap")
public class FsapProperties {

    private final Paths paths = new Paths();
    private final Ingest ingest = new Ingest();
    private final Views views = new Views();
    private final Report report = new Report();
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

    public Report getReport() {
        return report;
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
                "ERR_LOG",
                "PR_CFG"
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

    /**
     * 報表產出設定：額外要產出的業務分類，以及跨分類比較活頁簿。
     */
    public static class Report {
        private boolean materializeViews = true;
        private List<String> targetBzkinds = new ArrayList<>();
        private final CombinedWorkbook combinedWorkbook = new CombinedWorkbook();
        private boolean targetBzkindsConfigured;

        public boolean isMaterializeViews() {
            return materializeViews;
        }

        public void setMaterializeViews(boolean materializeViews) {
            this.materializeViews = materializeViews;
        }

        public List<String> getTargetBzkinds() {
            return targetBzkinds;
        }

        public void setTargetBzkinds(List<String> targetBzkinds) {
            this.targetBzkinds = targetBzkinds;
            this.targetBzkindsConfigured = targetBzkinds != null;
        }

        public CombinedWorkbook getCombinedWorkbook() {
            return combinedWorkbook;
        }

        public void validateConfiguration() {
            // 區分「未部署設定」與明確的空清單，避免漏設定時靜默降級成 ALL 月報。
            List<String> missing = new ArrayList<>();
            if (!targetBzkindsConfigured) {
                missing.add("fsap.report.target-bzkinds");
            }
            if (!combinedWorkbook.allSheetsConfigured) {
                missing.add("fsap.report.combined-workbook.all-sheets");
            }
            if (!combinedWorkbook.reportsConfigured) {
                missing.add("fsap.report.combined-workbook.reports");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Missing required report settings in external config/application.yml: "
                        + String.join(", ", missing)
                        + ". Load the complete external settings before generating reports; explicit empty lists are allowed.");
            }
        }
    }

    /**
     * 跨分類比較活頁簿：allSheets 固定排最前面（取 ALL 的結果），之後依 bzkinds 順序，
     * 每個分類依 reports 順序加入分頁。dataSource 是報表 CSV 檔名；sheetName 的 {pr_bzkind} 會換成分類代碼。
     * allSheets 與 reports 都是空的就不產出比較活頁簿。
     */
    public static class CombinedWorkbook {
        private List<SheetSpec> allSheets = new ArrayList<>();
        private List<String> bzkinds = new ArrayList<>();
        private List<SheetSpec> reports = new ArrayList<>();
        private boolean allSheetsConfigured;
        private boolean reportsConfigured;

        public List<SheetSpec> getAllSheets() {
            return allSheets;
        }

        public void setAllSheets(List<SheetSpec> allSheets) {
            this.allSheets = allSheets;
            this.allSheetsConfigured = allSheets != null;
        }

        public List<String> getBzkinds() {
            return bzkinds;
        }

        public void setBzkinds(List<String> bzkinds) {
            this.bzkinds = bzkinds;
        }

        public List<SheetSpec> getReports() {
            return reports;
        }

        public void setReports(List<SheetSpec> reports) {
            this.reports = reports;
            this.reportsConfigured = reports != null;
        }
    }

    public static class SheetSpec {
        private String sheetName;
        private String dataSource;

        public String getSheetName() {
            return sheetName;
        }

        public void setSheetName(String sheetName) {
            this.sheetName = sheetName;
        }

        public String getDataSource() {
            return dataSource;
        }

        public void setDataSource(String dataSource) {
            this.dataSource = dataSource;
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
