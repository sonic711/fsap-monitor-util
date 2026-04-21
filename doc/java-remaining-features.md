# Java 尚未完成轉換功能清單

本文件整理目前專案由 Python 移植到 Java 的現況，目的不是描述規劃，而是提供實際驗證時可對照的缺口清單。

更新日期：2026-04-21

## 已有 Java 對應的功能

以下 Python 主功能目前已有 Java 版本可用：

- `scripts/step1_ingest.py` -> Java `ingest`
- `scripts/update_views_to_db.py` -> Java `sync-views`
- `scripts/step2_report.py` -> Java `generate-report`
- `scripts/update_monitor_data/update_monitor_data.py` -> Java `update-monitor-data`
- `scripts/api_server.py` -> Java `/api/query`、`/api/schema`、`/health`
- `scripts/fsap-month-report-db.py` -> Java Web Dashboard 基礎版

## 部分完成，但尚未 1:1 等價的功能

### 1. `fsap-month-report-db.py` 互動式查詢頁

Java Web 版目前已有：

- 首頁 dashboard
- SQL 查詢區
- 查詢歷史
- 報表產物下載
- 監控資料下載
- schema snapshot

但仍未完整等價於原本 Streamlit 版本。

### 2. `api_server.py` 查詢 API 周邊行為

Java API 核心查詢已可用，但仍缺少部分舊版 API server 的附加行為與運維細節。

## 尚未轉換完成的功能清單

### A. 多分頁 SQL 查詢體驗

原 Python `fsap-month-report-db.py` 提供：

- 10 個獨立查詢 Tab
- 每個 Tab 各自維護 SQL 內容
- 每個 Tab 可分別執行

Java 目前狀態：

- 只有單一 SQL 查詢區
- 尚未提供多 Tab 或多查詢工作區

### B. `sql_history.json` 的查詢槽位保存與還原

原 Python 版本提供：

- `logs/sql_history.json`
- 保存 `sql_code_1` 到 `sql_code_10`
- 下次開啟 UI 時可還原各查詢槽位內容

Java 目前狀態：

- 已有 `query_history.log.jsonl` 形式的查詢歷史
- 尚未提供多槽位 SQL 保存/還原

### C. 查詢結果 TSV 匯出區

原 Python 版本提供：

- 將查詢結果轉成 TSV
- 使用者可直接複製貼到 Excel / Google Sheets

Java 目前狀態：

- 尚未提供 TSV 結果區

### D. 查詢結果 CSV / JSONL 前端匯出區

原 Python 版本提供：

- 頁面上直接顯示 CSV 文字
- 頁面上直接顯示 JSONL 文字
- 可直接下載 CSV / JSONL

Java 目前狀態：

- `/api/query` 會回 JSON
- 但前端尚未做查詢結果的 CSV / JSONL / TSV 匯出 UI

### E. 查詢結果表格化檢視

原 Python 版本提供：

- Streamlit dataframe 顯示
- 適合直接瀏覽結果

Java 目前狀態：

- 目前主要顯示原始 JSON
- 尚未做 HTML 表格檢視器

### F. 查詢結果欄位型別顯示

原 Python 版本提供：

- 顯示查詢結果的 `dtypes`

Java 目前狀態：

- 尚未提供查詢結果欄位型別面板

### G. 資料庫結構欄位明細展開

原 Python 版本提供：

- 側邊欄列出 table / view
- 可展開查看每個物件的欄位名稱與型別

Java 目前狀態：

- 已有 schema snapshot
- 尚未提供逐表欄位細節展開

### H. API request audit log

原 Python `api_server.py` 提供：

- 將每次 request method、path、body、status、duration 寫入 log

Java 目前狀態：

- 尚未實作等價的 API 請求日誌

### I. Web 端直接觸發任務

目前 Java 已有 CLI：

- `doctor`
- `sync-views`
- `ingest`
- `generate-report`
- `update-monitor-data`

但 Java Web 目前尚未提供：

- 在頁面上直接觸發上述任務
- 顯示任務執行狀態
- 顯示任務完成後的輸出位置

### J. Java 版啟動腳本或離線啟動包裝

原 Python 專案有：

- `scripts/start-fsap-month-report-db.sh`

Java 目前狀態：

- 可直接使用 `java -jar ...`
- 但尚未補 Java 專用啟動腳本、離線啟動包裝或整合執行入口

### K. 文件站建置流程整合

目前 repo 內尚有：

- `scripts/build_docs.js`

這不是 Python 主線功能，但目前也尚未整合進 Java / Maven 工作流。

## 建議驗證優先順序

若要自行驗證，建議依以下順序：

1. CLI 主線：`doctor`、`sync-views`、`ingest`、`generate-report`、`update-monitor-data`
2. API 主線：`/health`、`/api/query`、`/api/schema`
3. Web Dashboard：首頁、報表下載、監控資料下載
4. 再驗證本文件列出的缺口項目

## 結論

目前專案的核心資料處理與查詢主線，已大致完成 Java 化；尚未完成的部分，主要集中在原 Streamlit UI 的互動體驗、查詢保存機制，以及部分周邊運維功能。
