# Java 版本範圍與後續版本規劃

本文件用來定義 Java 版目前可交付範圍，以及後續版本要補的功能。  
它不再把所有未做項目都視為阻擋 release 的缺口，而是明確分成 `v1.0`、`v1.1`、`v1.2+`。

更新日期：2026-04-27

## 目前建置狀態

- 專案目前使用 `Gradle Wrapper 8.13` 建置。
- Spring Boot 版本為 `3.5.6`。
- Maven `pom.xml` 已移除，不再作為現行打包入口。
- 離線環境若要重新打包，請使用 `offline-maven-repo.zip` 與 `--offline -PofflineRepo=...`，流程見 `doc/java-gradle-offline-build.md`。

## v1.0 定義

`v1.0` 的目標是完成核心 Python 主線的 Java 化，並可在離線環境中以單一 `jar` 方式運作。

### v1.0 已納入的功能

- `scripts/step1_ingest.py` -> Java `ingest`
- `scripts/update_views_to_db.py` -> Java `sync-views`
- `scripts/step2_report.py` -> Java `generate-report`
- `scripts/update_monitor_data/update_monitor_data.py` -> Java `update-monitor-data`
- `scripts/api_server.py` -> Java `/api/query`、`/api/schema`、`/health`
- `scripts/fsap-month-report-db.py` -> Java Web Dashboard 基礎版
- 單一 `jar` 啟動模式
- `Windows` 與 `Linux` 執行相容

### v1.0 驗證重點

建議驗證順序如下：

1. CLI 主線：`doctor`、`sync-views`、`ingest`、`generate-report`、`update-monitor-data`
2. API 主線：`/health`、`/api/query`、`/api/schema`
3. Web Dashboard：首頁、查詢區、報表下載、監控資料下載、schema snapshot

## v1.1 目前狀態

`v1.1` 的主目標是把原本需要 `java -jar ... <command>` 執行的主線操作，補成可由 Web UI 直接操作的任務頁。

### v1.1 已完成

- 在 Web UI 直接觸發 `doctor`
- 在 Web UI 直接觸發 `sync-views`
- 在 Web UI 直接觸發 `ingest`
- 在 Web UI 直接觸發 `generate-report`
- 在 Web UI 直接觸發 `update-monitor-data`
- 在 UI 與 CLI 共用 `Generate Report` 參數模型，可調整：
  - `targetMonth`
  - `rangeStartDate`
  - `rangeEndDate`
  - `rangeStartTime`
  - `rangeEndTime`
  - `historyStartMonth`
  - `historyEndMonth`
- `reports/*.sql` 已改為 placeholder render，不必再手改硬編碼月份 / 日期
- 每次產出批次會保存 `report-params.json`
- 顯示目前執行中的任務狀態
- 顯示最近 UI 任務執行結果
- 顯示任務完成後的輸出路徑摘要
- 提供 `Ingest` 專用 `.xlsx` 上傳入口
- 在 UI 顯示 `01_excel_input` 內目前的 `.xlsx` 檔案清單
- 將頁面整理為兩個 tab：
  - `Operations`
  - `Query Console`
- 將主流程整理為明確 4 步 workflow：
  - `Doctor`
  - `Ingest`
  - `Sync Views`
  - `Generate Report`
- 在 UI 以步驟編號、狀態標示與鎖定狀態呈現 workflow
- 在後端 API 直接鎖順序，避免跳步執行
- `Recent Report Batches` 提供局部 refresh 按鈕，不必整頁重整

### v1.1 尚未完全收斂

- UI 任務歷史目前只存在記憶體，server 重啟後會清空
- `Monitor Data Exports` 區塊目前仍偏向初始載入，尚未做與報表區相同層級的局部 refresh
- `Task Status` 目前只顯示 output path 文字，尚未全面做成可點擊導向或直接下載
- `Update Monitor Data` 目前不在這條主線 workflow 鎖定內，仍視為額外操作

### v1.1 不要求

- 不要求完全重做 Streamlit 的多 Tab 查詢體驗
- 不要求先補齊所有查詢結果視覺化細節
- 不要求文件站建置流程同步 Java 化

## v1.2+ Backlog

以下項目不列入 `v1.0`，也不列入本次定義的 `v1.1`，統一移到 `v1.2+`：

### 1. 多分頁 SQL 查詢體驗

- 10 個獨立查詢 Tab
- 每個 Tab 各自維護 SQL 內容
- 每個 Tab 可分別執行

### 2. `sql_history.json` 的查詢槽位保存與還原

- 保存 `sql_code_1` 到 `sql_code_10`
- 下次開啟 UI 時可還原各查詢槽位內容

### 3. 查詢結果 TSV / CSV / JSONL 前端匯出區

- TSV 結果區
- CSV 文字區與下載
- JSONL 文字區與下載

### 4. 查詢結果表格化檢視

- HTML 表格檢視器
- 更接近原 Streamlit dataframe 的瀏覽方式

### 5. 查詢結果欄位型別顯示

- 查詢結果 `dtypes`
- 欄位型別面板

### 6. 資料庫結構欄位明細展開

- 逐表 / 逐 view 查看欄位名稱與型別

### 7. API request audit log

- 記錄 request method、path、body、status、duration

### 8. Java 啟動腳本與離線啟動包裝

- Java 專用 `.sh` / `.bat`
- 更完整的離線啟動入口

### 9. 文件站建置流程整合

- `scripts/build_docs.js` 的替代或整合策略

### 10. 報表交易類別參數化

- `7.x` / `8.x` 報表若要把 `TargetCategory` 也改成 UI/CLI 可控，仍需補白名單參數設計

詳細設計與目前第一版狀態請參考 `doc/java-report-parameterization-plan.md`。

## 結論

目前 `v1.0` 的功能範圍已足以支撐核心 ETL、報表、查詢 API 與基礎 Web Dashboard。  
`v1.1` 的 UI 任務操作頁也已經可用，後續剩餘工作主要是收斂互動細節與補強持久化。  
後續版本的重點應明確收斂為：

- `v1.1`：補齊任務頁收斂項目
- `v1.2+`：補齊 Streamlit 等價互動體驗與周邊運維能力
