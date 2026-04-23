# FSAP Python -> Java 全面移植與離線部署實作問題清單

> 狀態更新：v1.1 之後專案已改為 Gradle Wrapper 建置，`pom.xml` 已移除；離線部署改以 Gradle 產出的 Maven repository layout 搭配 `--offline -PofflineRepo=...` 建置。

本文件整理目前專案中所有已識別的 Python 使用點，並將「全面改寫為 Java」與「可完整移植到離線環境」所需先釐清的實作問題彙整成一份 kickoff 清單。

目標不是只列抽象風險，而是把後續真的會卡住設計、開發、測試、交付的決策點先攤開。

補充說明：本文件保留的是 **kickoff 時點的決策與問題盤點紀錄**，不等同於目前版本完成度；目前版本範圍請以 [java-remaining-features.md](/Users/sonic711/Desktop/development/fsap-monitor-util/doc/java-remaining-features.md) 為準。

---

## 0. 已確認決策

以下內容已由需求方確認，可直接作為後續實作前提：

| 項目 | 決策 |
| :--- | :--- |
| 核心資料引擎 | **保留 DuckDB** |
| Java 遷移原則 | 優先保留既有 SQL 資產，避免同時進行 SQL 引擎遷移 |
| 後續設計 | Java 技術選型、模組切分、Gradle 建置與離線 Maven repository 文件皆已補齊 |

這代表後續 Java 化應以「流程層重寫、資料引擎延用」為主軸，而不是重做整個資料平台。

---

## 1. 現況盤點

### 1.1 已識別的 Python 腳本

| 類別 | 檔案 | 目前責任 |
| :--- | :--- | :--- |
| ETL | `fsap-month-report-develop/scripts/step1_ingest.py` | 讀取 `01_excel_input/*.xlsx`，轉成 `02_source_lake/**/*.jsonl.gz` |
| ETL | `fsap-month-report-develop/scripts/update_views_to_db.py` | 將 `03_sql_logic/views/*.sql` 寫入 DuckDB 實體資料庫 |
| ETL / 報表 | `fsap-month-report-develop/scripts/step2_report.py` | 執行報表 SQL，輸出 Excel + CSV |
| API | `fsap-month-report-develop/scripts/api_server.py` | 提供 SQL 查詢 API |
| UI | `fsap-month-report-develop/scripts/fsap-month-report-db.py` | Streamlit 查詢儀表板 |
| 維運 | `fsap-month-report-develop/scripts/update_monitor_data/update_monitor_data.py` | 透過 API 擷取監控資料並輸出 CSV/JS |
| 啟動 | `fsap-month-report-develop/scripts/start-fsap-month-report-db.sh` | 建 `.venv`、安裝套件、啟動 Streamlit |

### 1.2 已識別的 Python / 執行期依賴

- `pandas`
- `openpyxl`
- `duckdb`
- `fastapi`
- `uvicorn`
- `streamlit`
- `requests`
- Python `venv`
- Shell 啟動腳本對 `python3` / `pip` 的安裝流程

### 1.3 目前最重要的技術事實

- 專案核心資料引擎是 `DuckDB`，而不是單純用 Python 做資料轉換。
- `03_sql_logic/views/*.sql` 明顯依賴 DuckDB 方言與函式，例如：
  - `read_json_auto`
  - `read_csv_auto`
  - `QUALIFY`
  - `try_strptime`
  - `regexp_extract`
  - `string_split` / `UNNEST`
- 報表邏輯大部分已經 SQL 化，因此 Java 移植不一定要重寫 SQL；但若不保留 DuckDB，成本會明顯放大。
- 這份文件建立當下尚無 Java 專案骨架；目前此項已完成，repo 內已有 `build.gradle`、Gradle Wrapper、`src/main/java`、`src/main/resources` 等結構。
- 目前文件大量仍假設使用者會安裝 Python、建立 `.venv`、並在線上安裝套件，這與「離線可完整移植」的目標衝突。

---

## 2. 建議先確認的總體方向

在進入逐支腳本改寫前，最先要定義的是「Java 版到底要保留哪些現有能力」。

### 2.1 資料引擎是否保留 DuckDB？

此項已確認：**保留 DuckDB**。

其直接影響如下：

- Java 可透過 JDBC 直接驅動 DuckDB。
- 現有 `views/*.sql` 與 `reports/*.sql` 原則上可延用。
- 移植重點將集中在：
  - Excel ingest
  - 報表輸出
  - API / UI 重建
  - 離線打包
- 可避免同時發生「語言移植 + SQL 方言重寫」的雙重風險。

因此，後續文件中凡涉及資料引擎選項，預設都以 DuckDB 為前提。

### 2.2 Java 版的交付型態是什麼？

需要先決定最終要交付哪一種：

1. 單一可執行 `fat jar`
2. CLI + Web API 同包
3. 多模組服務
4. 桌面 GUI
5. 純後端 + 前端靜態頁

目前從現況看，最接近的替代方案是：

- `CLI` 取代 `step1_ingest.py` / `update_views_to_db.py` / `step2_report.py`
- `Spring Boot` 或輕量 Java HTTP server 取代 `FastAPI`
- 新的 Web UI 取代 `Streamlit`

---

## 3. 需要你拍板的實作問題

以下問題是依目前程式碼現況整理，不是通用模板。

### 3.1 架構與範圍

1. Java 化是否包含 `scripts/fsap-month-report-db.py` 這個 Streamlit 儀表板，還是只先處理 ETL / API？
2. Java 化是否包含 `scripts/update_monitor_data/update_monitor_data.py` 這個監控資料同步工具？
3. Java 化是否包含 `scripts/start-fsap-month-report-db.sh` 的啟動行為，改成 Java 啟動器或安裝腳本？
4. 目標是「完全移除 Python 依賴」，還是允許文件產生、輔助工具暫時保留非 Java 元件？
5. `doc/install-python.md`、`doc/scripts.md`、`doc/readme.md` 等文件，是否要同步改為 Java / 離線版操作手冊？

### 3.2 資料庫與 SQL 策略

1. 已確認保留 `05_database/fsap-month-report.duckdb` 作為核心資料庫檔案；是否也要保留相同檔名與路徑？
2. DuckDB 目標版本要固定在哪一版？是否允許未來升級？
3. Java 端是否接受直接以 DuckDB JDBC 執行現有 SQL 檔？
4. `03_sql_logic/views/*.sql` 與 `03_sql_logic/reports/*.sql` 是否視為正式規格，不希望被大量重寫？
5. 目前 `step2_report.py` 會掃描 `03_sql_logic/macros`，但目錄實際不存在；Java 版是否要：
   - 移除 macro 載入流程
   - 還原 macro 目錄
   - 保留擴充點但允許為空
6. View 同步的「最多 3 輪重試」是否足夠，還是要改成真正的依賴排序 / DAG 分析？
7. 離線環境是否允許使用 DuckDB extension？若不允許，需保證所有 SQL 都不依賴執行期下載 extension。

### 3.3 Excel 匯入與數據湖格式

1. `step1_ingest.py` 目前只處理這 7 個 sheet，Java 版是否完全沿用？
   - `RT_CNT`
   - `RT_TMSPT`
   - `RT_PR_HH24`
   - `RT_NODE_HH24`
   - `BT_CNT`
   - `MON_LOG`
   - `ERR_LOG`
2. Excel 檔名規則 `FSAP每日交易統計YYYYMMDD.xlsx` 是否可視為固定契約？
3. 若遇到檔名不符規則的檔案，Java 版要跳過、報錯、還是移入 quarantine 目錄？
4. 現在輸出格式是 `.jsonl.gz`，Java 版是否必須維持完全相同格式，確保現有 SQL 不需改寫？
5. metadata 欄位是否必須維持完全一致？
   - `_file`
   - `_sheet`
   - `_dt`
   - `_ingest_ts`
6. Excel 讀取是否要保留目前 `dtype=object` 的寬鬆行為，還是改成明確 schema 驗證？
7. 若 Excel 有公式、格式異常、日期儲存格混型，Java 版要盡量容忍還是嚴格失敗？
8. 增量匯入判斷現在是「同日期 7 個目標檔都存在就跳過」，這個規則是否保留？

### 3.4 報表輸出

1. `step2_report.py` 目前會同時輸出：
   - 單一彙總 Excel
   - 每支報表各自一份 CSV
   Java 版是否必須完全保留？
2. Excel 格式要求要到什麼程度？
   - 只要資料正確
   - 欄寬自動調整即可
   - 需維持工作表命名與目前一致
   - 需維持既有編碼 / BOM / 開啟相容性
3. CSV 是否必須維持 `utf-8-sig`？
4. 若某一支報表 SQL 失敗，是否要像現在一樣把錯誤寫入 Excel 分頁，而不是整批失敗？
5. `reports/*.sql` 內硬編碼月份與參數的現況，Java 版是否要改成真正 CLI / 設定檔參數化？

### 3.5 API 與前端替代方案

1. `api_server.py` 要不要以 Java 重建成正式服務？
2. 若要重建，是否接受 `Spring Boot`？
3. SQL 查詢 API 是否仍允許直接接收任意 SQL 字串？
4. 若保留任意 SQL 查詢，離線環境的使用者角色是否可信任？是否需要白名單、read-only 保護或 SQL 限制？
5. `CORS allow_origins=["*"]` 在 Java 版是否仍可接受？
6. Streamlit 儀表板是否要用 Java 後端 + HTML/JS 前端重做，或乾脆降級成只保留 API + 匯出？
7. 現有 UI 的哪些能力必須保留？
   - 多分頁查詢
   - 查詢歷史
   - TSV 一鍵複製到 Excel
   - CSV / JSONL 下載
   - 資料庫 schema 側邊欄
8. 若 UI 也要離線部署，是否接受把前端靜態檔打包進 Java jar？

### 3.6 監控資料更新工具

1. `update_monitor_data.py` 目前是「呼叫本地 API，再輸出 CSV/JS」，Java 版是否還要維持這種 API-first 流程？
2. 若 Java 已可直接存取 DuckDB，這支工具是否改成直接查 DB，不再繞 HTTP？
3. `04_report_output/monitor-data/*.js` 這些 JS 產出是誰在使用？是否為必要交付物？
4. `config.json` 是否要保留為對外可編輯設定檔？
5. daily / hourly 的時間窗口規則是否固定：
   - daily 回溯 10 天
   - hourly 回溯 4 天

### 3.7 離線部署與供應鏈

1. 離線環境是完全無網路，還是只有無法連外網、但可用內部私有套件源？
2. 離線環境可否預先安裝 `JDK`，還是必須連 JRE/JDK 都一起打包？
3. 建置方式要用 `Maven` 還是 `Gradle`？
4. 若目標環境離線，是否接受在建置階段先把所有 Java 依賴下載完成，再交付：
   - `fat jar`
   - `lib/` 依賴目錄
   - 離線安裝包
5. 是否要求 `Windows` 與 `macOS` 都可離線部署？還是只需其中一個？
6. 若要跨平台，啟動入口要提供：
   - `.bat`
   - `.sh`
   - 單一 Java 啟動器
7. 是否允許使用 Docker？如果目標環境真的是離線內網，容器映像也需要預先封裝交付。
8. 除了 Java 依賴外，是否還允許保留 Node.js 來做 `doc/build_docs.js` 之類的文件生成？

### 3.8 記錄、設定與目錄結構

1. 目前所有腳本都依賴專案根目錄相對路徑；Java 版是否必須維持這個「零絕對路徑」特性？
2. `logs/*.log`、`logs/*.json`、`logs/*.jsonl` 的輸出格式是否要保持相容？
3. 設定檔是否統一改成：
   - `application.yml`
   - `json`
   - `properties`
4. `01_excel_input`、`02_source_lake`、`03_sql_logic`、`04_report_output`、`05_database` 這個目錄約定是否不可改？

### 3.9 測試與驗收

1. Java 移植完成後，驗收標準是什麼？
   - 與現有 Python 輸出逐檔比對
   - 與現有 DuckDB 查詢結果比對
   - 只驗主要報表數字
2. 是否有一批固定的回歸測試資料集可作 golden sample？
3. 對 `.xlsx`、`.csv`、`.jsonl.gz` 的比對容忍度要到哪裡？
   - 逐位元一致
   - 資料列一致即可
   - 格式差異可接受
4. 是否需要補自動化測試：
   - Excel ingest 測試
   - SQL 執行整合測試
   - 報表輸出測試
   - API 測試
5. 是否要求新舊系統並行一段時間，直到報表結果完全對齊才切換？

---

## 4. 我建議的預設答案

如果你希望先降低風險、縮短移植週期，建議以下列原則當成第一版：

1. **保留 DuckDB，不先遷移資料引擎** `已確認`
2. **先做 Java CLI + Java API，最後再處理 UI**
3. **沿用現有 `views/*.sql` 與 `reports/*.sql`**
4. **沿用既有目錄結構與輸出格式**
5. **Excel ingest 與報表輸出以結果相容為優先，不先追求 UI 完全等價**
6. **交付可離線執行的 fat jar + 啟動腳本 + 已固定版本的 JDK / 依賴包**
7. **先把 `update_monitor_data.py` 改為 Java 直接查 DuckDB，移除對本地 HTTP 的非必要依賴**

這樣可以把專案拆成三期：

| 階段 | 目標 | 風險 |
| :--- | :--- | :--- |
| Phase 1 | Java 化 ETL + Report + View Sync | 低到中 |
| Phase 2 | Java 化 API | 中 |
| Phase 3 | 重做 Streamlit UI | 中到高 |

---

## 5. 後續版本方向

本文件中的前置規劃工作已完成，後續可直接依版本路線推進：

1. `v1.0`
   - 以 Java CLI + API + 基礎 Web Dashboard 交付
2. `v1.1`
   - 補 Web UI 任務操作頁，讓 CLI 操作可由頁面觸發
3. `v1.2+`
   - 補強多 Tab 查詢、查詢槽位保存、結果匯出 UI、schema 細節與周邊運維能力

---

## 6. 補充觀察

### 6.1 目前最值得優先處理的兩個技術點

- **UI 替代策略**：這會決定是做單體 Web 工具，還是拆成 API + 前端。
- **Streamlit 是否必須等價重做**：這會直接決定 Java 版是否只是後端移植，還是包含完整產品重建。

### 6.2 目前看到的程式碼層級注意事項

- `step2_report.py` 會掃 `03_sql_logic/macros`，但目前目錄不存在，Java 版實作前應先定義此功能是否保留。
- `update_monitor_data.py` 目前對 `api_server.py` 有本地 HTTP 依賴；若目標是離線單機移植，這層很可能可以簡化。
- `api_server.py` 目前直接接受任意 SQL 字串，Java 版若要進一步產品化，建議一開始就定義安全邊界。

---

*文件更新於: 2026-04-22*
