# FSAP Java 模組切分與命令設計

> 狀態更新：v1.1 之後專案建置工具已由 Maven 改為 Gradle Wrapper。本文的模組邊界與命令設計仍有效；涉及 `pom.xml` 或 Maven module 的描述視為早期規劃背景。

本文件基於以下已確認技術方向整理：

- `Java CLI + Spring Boot + Thymeleaf/HTMX + DuckDB JDBC`
- 保留 `DuckDB`
- 保留既有 `03_sql_logic/views/*.sql` 與 `03_sql_logic/reports/*.sql`
- 目標可部署於離線環境

---

## 1. 設計目標

本次 Java 化的核心目標不是只把 Python 換語言，而是把目前分散在多支腳本中的能力，整理成：

1. 一套可重用的核心服務層
2. 一個統一的 CLI 入口
3. 一個可離線部署的 Web/API 入口
4. 一套穩定的設定、日誌與檔案路徑模型

---

## 2. 模組切分原則

### 2.1 分層原則

建議切成以下 4 層：

1. `core`
2. `cli`
3. `web`
4. `infra`

### 2.2 每層責任

| 模組層 | 責任 | 不應負責 |
| :--- | :--- | :--- |
| `core` | 業務流程、ETL、報表、DuckDB 操作協調 | 命令列解析、HTML 呈現 |
| `cli` | 命令列參數、命令路由、程序退出碼 | 直接寫業務邏輯 |
| `web` | REST API、Thymeleaf 頁面、下載入口 | 直接做 SQL 檔掃描/ETL 細節 |
| `infra` | 設定、I/O、Excel、JSON、CSV、日誌、DuckDB 連線 | 業務流程判斷 |

---

## 3. 建議模組結構

### 3.1 現行單一 Gradle 模組

第一階段可先維持單一 `Spring Boot` 專案，但在套件上切乾淨：

```text
src/main/java/com/fsap/monitor/
├── FsapApplication.java
├── cli/
├── web/
├── core/
├── infra/
└── shared/
```

### 3.2 若後續要演進成多模組

後續可再拆成：

```text
fsap-monitor-util/
├── settings.gradle
├── build.gradle
├── fsap-core/
├── fsap-cli/
├── fsap-web/
└── fsap-app/
```

但第一階段不必急著拆 Gradle multi-project build，先把套件邊界切清楚比較實際。

---

## 4. Java 套件建議

```text
com.fsap.monitor
├── cli
│   ├── command
│   └── config
├── web
│   ├── controller
│   ├── dto
│   └── view
├── core
│   ├── ingest
│   ├── report
│   ├── viewsync
│   ├── monitor
│   ├── query
│   └── service
├── infra
│   ├── duckdb
│   ├── excel
│   ├── csv
│   ├── json
│   ├── filesystem
│   └── logging
└── shared
    ├── model
    ├── exception
    └── util
```

---

## 5. 現有 Python 腳本對應設計

| 現有腳本 | Java 命令/模組 | 說明 |
| :--- | :--- | :--- |
| `step1_ingest.py` | `ingest` | Excel -> JSONL.gz |
| `update_views_to_db.py` | `sync-views` | 將 view SQL 載入 DuckDB |
| `step2_report.py` | `generate-report` | 執行報表 SQL 並輸出 Excel/CSV |
| `api_server.py` | `web` / `serve` | 提供 API 與網頁 |
| `fsap-month-report-db.py` | `web` / `serve` | 查詢 UI、歷史、schema 檢視 |
| `update_monitor_data.py` | `update-monitor-data` | 查 DB 輸出 CSV/JS |
| `start-fsap-month-report-db.sh` | `serve` + 啟動腳本 | `java -jar ... serve` |
| SFTP 每日來源下載 | `download-input` | 從 SFTP 備份目錄下載 Excel 到 `01_excel_input` |
| SFTP 月報回傳 | `upload-report` | 將最新月報 Excel 上傳回最近一次下載來源目錄 |

---

## 6. CLI 命令總覽

建議統一入口：

```bash
java -jar fsap-monitor-util.jar <command> [options]
```

建議命令如下：

1. `doctor`
2. `download-input`
3. `ingest`
4. `sync-views`
5. `generate-report`
6. `upload-report`
7. `update-monitor-data`
8. `serve`

---

## 7. 各命令設計

### 7.1 `ingest`

#### 用途

取代 `step1_ingest.py`，將 `01_excel_input/*.xlsx` 轉為 `02_source_lake/**/*.jsonl.gz`。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar ingest [--force] [--limit N] [--date YYYYMMDD]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--force` | 強制重轉 |
| `--limit N` | 只處理最新 N 份檔案 |
| `--date YYYYMMDD` | 只處理指定日期 |

輸入、輸出、DuckDB 與 SQL 目錄目前統一透過 Spring Boot 設定覆寫，例如 `--fsap.paths.base-dir=...`、`--fsap.paths.input-dir=...`、`--fsap.paths.source-lake-dir=...`。

#### Exit Code

| Code | 意義 |
| :--- | :--- |
| `0` | 全部成功 |
| `1` | 部分失敗 |
| `2` | 參數錯誤 |
| `3` | 找不到輸入檔 |

#### 主要 service

- `IngestCommand`
- `IngestService`
- `ProjectPathService`

---

### 7.2 `sync-views`

#### 用途

取代 `update_views_to_db.py`，將 `03_sql_logic/views/*.sql` 載入 `DuckDB`。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar sync-views [--max-rounds 3] [--fail-fast]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--max-rounds N` | View 重試輪數 |
| `--fail-fast` | 任一失敗立即結束 |

views 目錄與 DuckDB 路徑目前統一透過 `fsap.paths.sql-logic-dir` 與 `fsap.paths.database-file` 覆寫。

#### 主要 service

- `SyncViewsCommand`
- `ViewSyncService`
- `ProjectPathService`
- `DuckDbConnectionFactory`

#### 備註

目前實作採用：

- 先依 view 之間的相依關係排序
- 再執行多輪重試作為 fallback

也就是說，`sync-views` 已不是單純依檔名反覆重試，而是先做依賴排序，再保留容錯重試。

---

### 7.3 `generate-report`

#### 用途

取代 `step2_report.py`，執行 `reports/*.sql`，輸出彙總 Excel 與個別 CSV。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar generate-report [--timestamp YYYYMMDDHHMM] [--continue-on-error] [report parameters...]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--timestamp TS` | 指定批次目錄時間戳 |
| `--continue-on-error` | 單支報表失敗不終止整批 |
| `--target-month YYYY-MM` | 指定報表月份 |
| `--range-start-date YYYY-MM-DD` | 指定每日報表起日 |
| `--range-end-date YYYY-MM-DD` | 指定每日報表迄日 |
| `--range-start-time "YYYY-MM-DD HH:mm"` | 指定明細報表起始時間 |
| `--range-end-time "YYYY-MM-DD HH:mm"` | 指定明細報表結束時間 |
| `--history-start-month YYYY-MM` | 指定歷史區間起始月份 |
| `--history-end-month YYYY-MM` | 指定歷史區間結束月份 |

輸出目錄、報表 SQL 目錄與 DuckDB 路徑目前統一透過 `fsap.paths.report-output-dir`、`fsap.paths.sql-logic-dir` 與 `fsap.paths.database-file` 覆寫。

#### 主要 service

- `GenerateReportCommand`
- `ReportGenerationService`
- `ReportGenerationRequest`
- `ReportParameterDefaultsService`

#### 必保行為

- 每次執行建立獨立時間戳目錄
- 產出單一彙總 Excel
- 每個 SQL 另產一份 CSV
- 單一報表失敗可寫錯誤工作表並繼續

---

### 7.4 `update-monitor-data`

#### 用途

取代 `update_monitor_data.py`，直接查 DuckDB 輸出監控資料，不再繞 HTTP。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar update-monitor-data [--config PATH]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--config PATH` | 設定檔路徑 |

DuckDB 路徑與輸出根目錄目前統一透過 `fsap.paths.database-file` 與 `fsap.paths.report-output-dir` 覆寫。

#### 主要 service

- `UpdateMonitorDataCommand`
- `MonitorDataExportService`
- `ViewSyncService`
- `DuckDbConnectionFactory`
- `ArtifactBrowseService`

#### 必保行為

- daily 回溯 10 天
- hourly 回溯 4 天
- 輸出 CSV
- 輸出 JS 包裝檔
- Web UI 可從 `/api/tasks/monitor-files` 取得下載清單
- Web UI 可從 `/api/tasks/monitor-data?limit=10000` 取得互動表格 / 圖表資料

---

### 7.5 `serve`

#### 用途

啟動 `Spring Boot` Web/API，取代 `FastAPI + Streamlit`。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar serve [--port 8080]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--port N` | Web port |
| `--host HOST` | 綁定 host |
| `--readonly` | 強制唯讀模式 |
| `--writable` | 啟用 Web UI 任務操作 |

DuckDB 路徑目前統一透過 `fsap.paths.database-file` 覆寫。

#### 主要職責

- 提供查詢頁
- 提供查詢 API
- 提供 schema 檢視
- 提供 CSV / JSONL 下載
- 提供健康檢查端點

---

### 7.6 `download-input`

#### 用途

從 SFTP 備份目錄下載每日交易統計 Excel 到 `01_excel_input`。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar download-input [--date yyyyMMdd] [--filename NAME] [--remote-root PATH] [--local-dir PATH] [--overwrite]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--date yyyyMMdd` | 目標西元日期，預設為 Asia/Taipei 今日 |
| `--filename NAME` | 覆寫遠端檔名 |
| `--remote-root PATH` | 覆寫遠端備份根目錄，預設 `/FSAP/FILE_BCKP` |
| `--local-dir PATH` | 覆寫本機下載目錄 |
| `--overwrite` | 本機已存在同名檔案時覆蓋 |

#### 主要 service

- `DownloadInputCommand`
- `SftpInputDownloadService`

---

### 7.7 `upload-report`

#### 用途

將最新產出的月報 Excel 上傳回 SFTP。預設使用 `download-input` 寫入的 `logs/latest-sftp-download.json` 判斷遠端目錄。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar upload-report [--local-file PATH] [--remote-dir PATH] [--metadata-file PATH] [--overwrite]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--local-file PATH` | 指定要上傳的 `.xlsx`，未指定時使用最新報表批次中的 workbook |
| `--remote-dir PATH` | 指定遠端目錄，未指定時讀取最近一次下載 metadata |
| `--metadata-file PATH` | 覆寫最近一次下載 metadata 檔 |
| `--overwrite` | 遠端已存在同名報表時覆蓋 |

#### 主要 service

- `UploadReportCommand`
- `SftpReportUploadService`

---

### 7.8 `doctor`

#### 用途

提供環境自檢，這在離線部署非常重要。

#### 建議語法

```bash
java -jar fsap-monitor-util.jar doctor
```

#### 檢查項目

- `01_excel_input` 是否存在
- `03_sql_logic/views` 是否存在
- `03_sql_logic/reports` 是否存在
- `05_database` 是否可寫
- DuckDB JDBC 是否可連線
- 必要目錄是否可建立

#### 主要 service

- `DoctorCommand`
- `EnvironmentCheckService`

---

## 8. Web 模組設計

### 8.1 Controller 切分

| Controller | 路徑 | 責任 |
| :--- | :--- | :--- |
| `PageController` | `/` | 首頁與查詢頁 |
| `QueryController` | `/api/query`、`/api/schema` | 執行查詢、列表 table/view 與欄位 |
| `TaskController` | `/api/tasks/*` | UI 任務觸發、任務狀態、輸入檔 / 報表 / monitor 產物查詢 |
| `FileDownloadController` | `/downloads/file` | 依安全相對路徑下載報表、monitor CSV/JS 與輸入檔 |
| `HealthController` | `/health` | 健康檢查 |

### 8.2 頁面切分

| 頁面 | 用途 |
| :--- | :--- |
| `query.html` | 主要 Web UI，包含 `Operations`、`Monitor Dashboard`、`Query Console` 頁籤 |
| `fragments/result-table.html` | 查詢結果表格 |
| `fragments/schema-tree.html` | schema 側欄 |
| `fragments/history-list.html` | 查詢歷史 |

`Monitor Dashboard` 目前直接放在 `query.html`，沒有拆獨立 template。它使用本機靜態資源 `static/vendor/chartjs/chart.umd.min.js`，提供 monitor CSV 互動表格與趨勢折線圖。

### 8.3 HTMX 使用範圍

建議只用在這些互動：

- 提交 SQL 查詢
- 載入 schema 區塊
- 載入歷史紀錄
- 更新查詢結果區塊

不要第一版就做成重 JS 前端。

---

## 9. Core Service 切分

### 9.1 建議核心服務

| Service | 責任 |
| :--- | :--- |
| `ProjectPathService` | 管理專案相對路徑 |
| `DuckDbConnectionFactory` | 建立 DuckDB JDBC 連線 |
| `ExcelIngestService` | 執行 ingest 主流程 |
| `ViewSyncService` | 執行 view 載入 |
| `ReportGenerationService` | 執行報表批次 |
| `QueryService` | 執行查詢與結果封裝 |
| `SchemaBrowseService` | 讀取資料庫 schema |
| `MonitorDataService` | 產出監控資料 |
| `HistoryService` | 寫入與讀取查詢歷史 |

### 9.2 關鍵原則

- CLI 與 Web 都應共用 `core` service
- 不要在 controller 或 command 中直接寫 SQL 檔掃描、Excel 讀寫、CSV 邏輯

---

## 10. 設定檔設計

### 10.1 建議主設定檔

使用：

- `application.yml`

### 10.2 建議欄位

```yaml
fsap:
  paths:
    base-dir: .
    input-dir: 01_excel_input
    source-lake-dir: 02_source_lake
    sql-logic-dir: 03_sql_logic
    report-output-dir: 04_report_output
    database-file: 05_database/fsap-month-report.duckdb
    log-dir: logs
  ingest:
    target-sheets:
      - RT_CNT
      - RT_TMSPT
      - RT_PR_HH24
      - RT_NODE_HH24
      - BT_CNT
      - MON_LOG
      - ERR_LOG
    filename-pattern: "FSAP每日交易統計(\\d{8})\\.xlsx"
  views:
    max-rounds: 3
  web:
    readonly: true
  monitor:
    config-file: config/monitor-data.json
```

### 10.3 其他設定檔

建議保留：

- `config/monitor-data.json`

因為它本來就是任務配置，比硬編碼在 `application.yml` 更清楚。

---

## 11. 輸出與歷史檔設計

### 11.1 建議保留的 log/history 檔案

| 檔案 | 是否保留 | 說明 |
| :--- | :--- | :--- |
| `logs/ingest.log` | 保留 | ingest 執行紀錄 |
| `logs/view_sync.log` | 保留 | view 載入紀錄 |
| `logs/report_execution.log` | 保留 | 報表產出紀錄 |
| `logs/api_server.log` | 可整併 | 可整併入 Spring Boot log |
| `logs/sql_history.json` | 保留 | 查詢內容快照 |
| `logs/query_history.log.jsonl` | 保留 | 查詢歷史 |

### 11.2 建議原則

- 檔名盡量與現有系統相容
- 便於新舊系統並行比對

---

## 12. 第一階段實作順序

建議依以下順序進行：

1. 建 `Gradle Wrapper + Spring Boot + picocli` 骨架
2. 實作 `ProjectPathService` 與 `DuckDbConnectionFactory`
3. 實作 `ingest`
4. 實作 `sync-views`
5. 實作 `generate-report`
6. 實作 `doctor`
7. 實作 `download-input` / `upload-report`
8. 實作 `update-monitor-data`
9. 最後再實作 `serve`

理由很簡單：

- ETL 與報表是核心價值
- Web UI 是替代層，不應卡住主流程遷移

---

## 13. 驗收建議

每個命令都應有最低驗收標準：

### 13.1 `ingest`

- 能正確掃描 Excel
- 能產出相同路徑與命名的 `.jsonl.gz`
- metadata 欄位齊全

### 13.2 `sync-views`

- 能成功載入全部 view
- 重試策略結果與現況一致

### 13.3 `generate-report`

- 產出彙總 Excel
- 產出各報表 CSV
- 主要報表數字與 Python 版一致

### 13.4 `update-monitor-data`

- 產出相同命名的 CSV/JS
- daily/hourly 規則一致

### 13.5 `serve`

- `/health` 可正常回應
- 可執行 SQL 查詢
- 可顯示 schema
- 可下載 CSV / JSONL

---

## 14. 最終建議

第一版請避免做以下事情：

1. 不要先重寫 SQL
2. 不要先拆多個獨立程序
3. 不要先導入重型前端框架
4. 不要先做過度抽象的 plugin 架構

先把這個專案穩定地收斂成：

- 一個 `jar`
- 一組命令
- 一套共用 service
- 一個離線可跑的 Web UI

這才是最符合目前需求的切法。

---

*文件更新於: 2026-04-21*
