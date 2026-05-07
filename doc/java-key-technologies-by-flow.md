# Java 專案關鍵技術與執行流程說明

本文件以「程式執行流程」為主軸，整理此專案目前使用到的關鍵技術、主要套件、核心程式位置，以及每一段流程實際在做什麼。

目的是讓維護者可以快速回答這幾類問題：

- 專案啟動時用到哪些技術？
- Web UI / API 是怎麼接起來的？
- Excel 是如何轉成 JSONL.GZ 的？
- DuckDB 是在哪裡被操作的？
- 最終報表 Excel 是怎麼產出的？
- 若要改某段流程，應先看哪個類別？

---

## 1. 專案啟動與建置

### 1.1 建置工具

- 技術：Gradle
- 主要檔案：`build.gradle`、`settings.gradle`
- 用途：
  - 管理相依套件
  - 編譯 Java 17
  - 產出 Spring Boot 可執行 jar
  - 準備離線 Maven repository

### 1.2 主框架

- 技術：Spring Boot 3.5.6
- 套件：
  - `org.springframework.boot:spring-boot-starter`
  - `org.springframework.boot:spring-boot-starter-web`
  - `org.springframework.boot:spring-boot-starter-thymeleaf`
  - `org.springframework.boot:spring-boot-starter-validation`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/FsapApplication.java`
- 用途：
  - 啟動整個應用程式
  - 判斷目前是 CLI 模式還是 Web 模式
  - 在 `serve` 指令下啟動 Spring MVC / Thymeleaf 網站

### 1.3 啟動流程

啟動入口是：

- `com.fsap.monitor.FsapApplication`

實際流程：

1. 讀取啟動參數
2. 拆分 Spring Boot 參數與 CLI 指令參數
3. 若第一個指令是 `serve`
   - 啟動 Web 模式
4. 否則
   - 啟動非 Web 模式
   - 交給 Picocli 執行 CLI 指令

### 1.4 CLI 技術

- 技術：Picocli
- 套件：`info.picocli:picocli:4.7.6`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/cli/FsapCli.java`
  - `src/main/java/com/fsap/monitor/cli/PicocliRunner.java`
  - `src/main/java/com/fsap/monitor/cli/PicocliSpringFactory.java`
  - `src/main/java/com/fsap/monitor/cli/command/*.java`
- 用途：
  - 提供 CLI 指令入口
  - 讓 CLI 指令可以使用 Spring 的依賴注入

目前主要 CLI 指令：

- `doctor`
- `ingest`
- `sync-views`
- `generate-report`
- `update-monitor-data`
- `serve`

---

## 2. Web UI 與 API 流程

### 2.1 頁面渲染

- 技術：Thymeleaf
- 套件：`spring-boot-starter-thymeleaf`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/web/controller/PageController.java`
  - `src/main/resources/templates/query.html`
- 用途：
  - 渲染首頁 dashboard
  - 把任務狀態、輸入檔列表、最近報表、查詢歷史、schema、報表預設值一次帶進頁面

### 2.2 REST API

- 技術：Spring MVC / REST Controller
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/web/controller/TaskController.java`
  - `src/main/java/com/fsap/monitor/web/controller/QueryController.java`
  - `src/main/java/com/fsap/monitor/web/controller/FileDownloadController.java`
  - `src/main/java/com/fsap/monitor/web/controller/HealthController.java`
- 用途：
  - 任務觸發
  - SQL 查詢
  - 下載報表與產物
  - 健康檢查

### 2.3 前端互動現況

- 技術：Thymeleaf + 原生 JavaScript
- 模板位置：`src/main/resources/templates/query.html`
- 補充：
  - 目前專案沒有實際使用 HTMX
  - 模板中沒有 `hx-*` 屬性，也沒有 htmx script

---

## 3. Doctor 預檢流程

### 3.1 使用技術

- 技術：JDK 檔案系統 API + DuckDB JDBC
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/service/EnvironmentCheckService.java`
  - `src/main/java/com/fsap/monitor/cli/command/DoctorCommand.java`
  - `src/main/java/com/fsap/monitor/core/task/TaskExecutionService.java`

### 3.2 做的事情

`Doctor` 會檢查：

- base directory 是否存在
- input directory 是否存在
- views / reports 目錄是否存在
- 資料庫父目錄是否可寫
- log 目錄是否可寫
- DuckDB 是否可連線

### 3.3 目的

這一段不是深度診斷，而是確認後續：

- `ingest`
- `sync-views`
- `generate-report`

至少有基本執行條件。

---

## 4. Excel 上傳與輸入檔管理

### 4.1 使用技術

- 技術：Spring Multipart Upload
- 主要套件：
  - Spring Web `MultipartFile`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/ingest/InputUploadService.java`
  - `src/main/java/com/fsap/monitor/web/controller/TaskController.java`

### 4.2 做的事情

當使用者透過 UI 上傳 `.xlsx` 時：

1. 驗證副檔名是否為 `.xlsx`
2. 先寫入暫存檔 `.uploading`
3. 成功後再原子或非原子搬移到 `01_excel_input`

### 4.3 這段使用的關鍵技術

- `MultipartFile`
- `Files.move(...)`
- `StandardCopyOption.ATOMIC_MOVE`

---

## 5. Excel 轉 JSONL.GZ

這一段就是你提到的「Excel 轉 JSONL」核心流程。

### 5.1 使用技術

- 技術：Apache POI
- 套件：`org.apache.poi:poi-ooxml:5.3.0`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/ingest/IngestService.java`
  - `src/main/java/com/fsap/monitor/FsapApplication.java`

### 5.2 相關套件與技術

- `WorkbookFactory`
  - 用途：讀取 `.xlsx`
- `Cell / Row / Sheet`
  - 用途：逐列逐欄讀 Excel
- `DateUtil`
  - 用途：判斷 Excel 數字欄是否其實是日期
- `ZipSecureFile.setMinInflateRatio(0.0d)`
  - 用途：關閉 POI 對 zip bomb inflate ratio 的限制

### 5.3 JSON 輸出技術

- 技術：Jackson
- 套件：`com.fasterxml.jackson.core:jackson-databind`
- 程式位置：
  - `src/main/java/com/fsap/monitor/core/ingest/IngestService.java`
- 用途：
  - 把每一列資料轉成 JSON 字串

### 5.4 壓縮輸出技術

- 技術：GZIP
- 套件：JDK 內建 `java.util.zip.GZIPOutputStream`
- 程式位置：
  - `src/main/java/com/fsap/monitor/core/ingest/IngestService.java`
- 用途：
  - 產出 `.jsonl.gz`

### 5.5 執行流程

`IngestService.ingest(...)` 的主要流程是：

1. 掃描 `01_excel_input` 中符合命名規則的 `.xlsx`
2. 依日期 / limit 過濾
3. 逐一讀取 workbook
4. 找出該 workbook 內實際存在的目標 sheet
5. 判斷對應輸出是否已存在
6. 若需要處理，將每列資料轉成 JSON
7. 寫出到：
   - `02_source_lake/<SHEET>/<SHEET>-YYYYMMDD.jsonl.gz`

### 5.6 額外加上的欄位

每筆 JSON 資料除了 Excel 原始欄位外，還會補：

- `_file`
- `_sheet`
- `_dt`
- `_ingest_ts`

用途是後續：

- SQL 查詢
- 報表除錯
- 來源追蹤

---

## 6. DuckDB 與資料庫操作

這一段是你提到的「DB 相關操作」核心。

### 6.1 使用技術

- 技術：DuckDB JDBC
- 套件：`org.duckdb:duckdb_jdbc:1.5.2.0`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/infra/duckdb/DuckDbConnectionFactory.java`

### 6.2 連線方式

程式透過：

- `DriverManager.getConnection("jdbc:duckdb:" + dbPath)`

建立 DuckDB 檔案型資料庫連線。

### 6.3 實際有操作 DB 的地方

#### 查詢 SQL Console

- 程式位置：
  - `src/main/java/com/fsap/monitor/core/query/QueryService.java`
- 技術：
  - JDBC `Connection`
  - `Statement`
  - `ResultSet`
- 用途：
  - 執行 UI 的 SQL 查詢

#### Schema 檢視

- 程式位置：
  - `src/main/java/com/fsap/monitor/core/query/SchemaBrowseService.java`
- 用途：
  - 從 `information_schema.tables` 讀出目前 table / view 清單

#### View 同步

- 程式位置：
  - `src/main/java/com/fsap/monitor/core/viewsync/ViewSyncService.java`
- 用途：
  - 執行 `03_sql_logic/views/*.sql`
  - `DROP VIEW IF EXISTS`
  - 建立 / 重建 views

#### 報表 SQL 執行

- 程式位置：
  - `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`
- 用途：
  - 執行 `03_sql_logic/reports/*.sql`
  - 把查詢結果轉成 Excel sheet 與 CSV

#### Monitor Data 匯出

- 程式位置：
  - `src/main/java/com/fsap/monitor/core/monitor/MonitorDataExportService.java`
- 用途：
  - 對指定 view 做查詢
  - 輸出 monitor 用 CSV / JS

---

## 7. SQL Macro 與 View 同步

### 7.1 使用技術

- 技術：JDBC + SQL 檔掃描
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/viewsync/ViewSyncService.java`
  - `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`
  - `src/main/java/com/fsap/monitor/core/service/ProjectPathService.java`

### 7.2 Macro 載入

在產報表前，`ReportGenerationService` 會先載入：

- `03_sql_logic/macros/*.sql`

用途：

- 把共用 SQL 函式或工具先註冊進 DuckDB

### 7.3 View 同步

`ViewSyncService` 會：

1. 掃描 `03_sql_logic/views/*.sql`
2. 先用字串規則做依賴排序
3. 先 `DROP VIEW IF EXISTS`
4. 逐輪重試建立 views

### 7.4 為什麼需要 retry round

因為 view 之間有相依關係，單靠一次靜態排序不一定能 100% 解開所有依賴，所以保留多輪重試，直到：

- 全部成功
- 或某輪完全沒有進度

---

## 8. 報表參數解析

### 8.1 使用技術

- 技術：Java Time API
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/report/ReportParameterDefaultsService.java`
  - `src/main/java/com/fsap/monitor/core/report/ReportGenerationRequest.java`

### 8.2 做的事情

這一層負責把 UI 或 CLI 傳進來的較少參數，補成 SQL 真正需要的完整參數。

目前預設規則：

- `Reporting Month`：本月的上個月
- `History Month End`：同 `Reporting Month`
- `History Month Start`：固定 `2025-09`

並自動推導：

- `rangeStartDate`
- `rangeEndDate`
- `rangeStartTime`
- `rangeEndTime`

---

## 9. 最終報表 Excel 匯出

這一段是你提到的「如何匯出最終報表 Excel」核心。

### 9.1 使用技術

- 技術：Apache POI XSSF
- 套件：`org.apache.poi:poi-ooxml`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`

### 9.2 使用到的 POI 能力

- `XSSFWorkbook`
  - 建立整份 Excel workbook
- `Sheet / Row / Cell`
  - 寫入各分頁內容
- `CellStyle / DataFormat`
  - 設定數字欄位格式
- `WorkbookUtil`
  - 處理 sheet 名稱長度與非法字元

### 9.3 報表輸出流程

`ReportGenerationService.generate(...)` 的主要流程：

1. 解析報表參數
2. 建立輸出目錄 `04_report_output/<timestamp>/`
3. 寫出 `report-params.json`
4. 開啟 DuckDB connection
5. 建立 `XSSFWorkbook`
6. 載入 macro
7. sync views
8. 掃描 `03_sql_logic/reports/*.sql`
9. 按檔名前綴數字排序
10. 逐支執行 SQL
11. 將結果寫入 workbook sheet
12. 同時輸出對應 CSV
13. 最後寫出整份 `.xlsx`

### 9.4 數字欄位格式

Excel 報表中的數字欄位會套用：

- 整數：千分位格式
- 小數：千分位加小數格式

對應程式位置：

- `createWorkbookStyles(...)`
- `writeCellValue(...)`

都在：

- `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`

### 9.5 分頁排序

報表分頁不是依字串排序，而是依數字前綴做階層式排序，例如：

- `1`
- `1.1`
- `2`
- `2.1`

對應程式位置：

- `reportFileComparator(...)`
- `extractReportOrder(...)`

### 9.6 Pivot 無資料保護

若某些動態 pivot 報表在指定期間完全沒有資料，DuckDB 可能會因欄位展不出來而報錯。

目前程式會：

- 改輸出提示頁
- 不直接讓整批報表中止

對應程式位置：

- `executeReportQuery(...)`

---

## 10. CSV 與 Monitor Data 匯出

### 10.1 使用技術

- 技術：Apache Commons CSV
- 套件：`org.apache.commons:commons-csv:1.11.0`
- 主要程式位置：
  - `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`
  - `src/main/java/com/fsap/monitor/core/monitor/MonitorDataExportService.java`

### 10.2 用途

#### 報表 sidecar CSV

每支報表 SQL 除了寫進 workbook，還會另外輸出一份 CSV。

#### monitor-data

`update-monitor-data` 會從指定 view 查資料，輸出：

- `.csv`
- `.js`

供監控頁面或前端資產直接使用。

---

## 11. JSON 與紀錄檔

### 11.1 使用技術

- 技術：Jackson `ObjectMapper`
- 套件：`com.fasterxml.jackson.core:jackson-databind`

### 11.2 使用位置

#### Ingest

- 把 Excel 列資料轉成 JSONL

位置：

- `src/main/java/com/fsap/monitor/core/ingest/IngestService.java`

#### Report Parameters Snapshot

- 把當次報表參數寫成 `report-params.json`

位置：

- `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`

#### Query History

- 把查詢歷史寫成 JSONL

位置：

- `src/main/java/com/fsap/monitor/core/query/QueryHistoryService.java`

#### Monitor Config

- 讀取 monitor export 設定 JSON

位置：

- `src/main/java/com/fsap/monitor/core/monitor/MonitorDataExportService.java`

---

## 12. 重要流程總結

### 12.1 Web 模式

1. `FsapApplication`
2. Spring Boot 啟動
3. `PageController` 渲染 `query.html`
4. 使用者操作 UI
5. `TaskController / QueryController / FileDownloadController`
6. 對應 service 執行實際工作

### 12.2 Ingest 流程

1. 上傳 `.xlsx`
2. `InputUploadService`
3. `IngestService`
4. Apache POI 讀 Excel
5. Jackson 轉 JSON
6. GZIP 壓縮
7. 輸出到 `02_source_lake`

### 12.3 報表流程

1. `ReportGenerationService`
2. `ReportParameterDefaultsService`
3. `DuckDbConnectionFactory`
4. 載入 macros
5. `ViewSyncService`
6. 執行 `reports/*.sql`
7. Apache POI 寫 `.xlsx`
8. Commons CSV 寫 `.csv`
9. 輸出到 `04_report_output/<timestamp>/`

---

## 13. 維護時的快速定位建議

若你要改的是：

- 啟動方式
  - 先看 `FsapApplication.java`
- CLI 指令
  - 先看 `cli/command/*.java`
- UI 畫面
  - 先看 `PageController.java` 與 `templates/query.html`
- Excel 轉 JSONL
  - 先看 `IngestService.java`
- 上傳 `.xlsx`
  - 先看 `InputUploadService.java`
- DuckDB 連線
  - 先看 `DuckDbConnectionFactory.java`
- View 載入順序或失敗
  - 先看 `ViewSyncService.java`
- SQL 查詢頁
  - 先看 `QueryService.java`
- 報表預設月份 / 日期
  - 先看 `ReportParameterDefaultsService.java`
- 最終報表 Excel 格式
  - 先看 `ReportGenerationService.java`
- Monitor data 匯出
  - 先看 `MonitorDataExportService.java`

