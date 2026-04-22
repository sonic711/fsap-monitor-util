# FSAP Java 模組切分與命令設計

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

### 3.1 若先做單一 Maven 模組

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
├── pom.xml
├── fsap-core/
├── fsap-cli/
├── fsap-web/
└── fsap-app/
```

但第一階段不必急著拆 Maven reactor，先把套件邊界切清楚比較實際。

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

---

## 6. CLI 命令總覽

建議統一入口：

```bash
java -jar fsap-monitor-util.jar <command> [options]
```

建議命令如下：

1. `ingest`
2. `sync-views`
3. `generate-report`
4. `update-monitor-data`
5. `serve`
6. `doctor`

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
| `--input-dir PATH` | 覆寫 Excel 輸入目錄 |
| `--output-dir PATH` | 覆寫 source lake 目錄 |

#### Exit Code

| Code | 意義 |
| :--- | :--- |
| `0` | 全部成功 |
| `1` | 部分失敗 |
| `2` | 參數錯誤 |
| `3` | 找不到輸入檔 |

#### 主要 service

- `IngestCommand`
- `ExcelIngestService`
- `ExcelSheetReader`
- `JsonlGzipWriter`
- `IngestLogService`

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
| `--views-dir PATH` | 覆寫 views 目錄 |
| `--db PATH` | 覆寫 DuckDB 路徑 |

#### 主要 service

- `SyncViewsCommand`
- `ViewSyncService`
- `SqlFileLoader`
- `DuckDbExecutor`

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
java -jar fsap-monitor-util.jar generate-report [--timestamp YYYYMMDDHHMM] [--report-dir PATH]
```

#### 建議參數

| 參數 | 說明 |
| :--- | :--- |
| `--timestamp TS` | 指定批次目錄時間戳 |
| `--report-dir PATH` | 覆寫輸出目錄 |
| `--reports-dir PATH` | 覆寫 SQL 報表目錄 |
| `--db PATH` | 覆寫 DuckDB 路徑 |
| `--continue-on-error` | 單支報表失敗不終止整批 |

#### 主要 service

- `GenerateReportCommand`
- `ReportGenerationService`
- `ReportSqlRunner`
- `ExcelWorkbookWriter`
- `CsvReportWriter`

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
| `--db PATH` | 覆寫 DuckDB 路徑 |
| `--output-dir PATH` | 覆寫輸出目錄 |

#### 主要 service

- `UpdateMonitorDataCommand`
- `MonitorDataService`
- `MonitorQueryBuilder`
- `CsvOutputWriter`
- `JsOutputWriter`

#### 必保行為

- daily 回溯 10 天
- hourly 回溯 4 天
- 輸出 CSV
- 輸出 JS 包裝檔

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
| `--db PATH` | 覆寫 DuckDB 路徑 |

#### 主要職責

- 提供查詢頁
- 提供查詢 API
- 提供 schema 檢視
- 提供 CSV / JSONL 下載
- 提供健康檢查端點

---

### 7.6 `doctor`

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
| `QueryController` | `/api/query` | 執行查詢 |
| `SchemaController` | `/api/schema` | 列表 table/view 與欄位 |
| `HistoryController` | `/api/history` | 查詢歷史 |
| `DownloadController` | `/download/*` | 匯出 CSV/JSONL |
| `HealthController` | `/health` | 健康檢查 |

### 8.2 頁面切分

| 頁面 | 用途 |
| :--- | :--- |
| `query.html` | 主要 SQL 查詢頁 |
| `fragments/result-table.html` | 查詢結果表格 |
| `fragments/schema-tree.html` | schema 側欄 |
| `fragments/history-list.html` | 查詢歷史 |

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

1. 建 `Maven + Spring Boot + picocli` 骨架
2. 實作 `ProjectPathService` 與 `DuckDbConnectionFactory`
3. 實作 `ingest`
4. 實作 `sync-views`
5. 實作 `generate-report`
6. 實作 `doctor`
7. 實作 `update-monitor-data`
8. 最後再實作 `serve`

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
