# FSAP Java 技術選型建議

> 狀態更新：v1.1 之後建置工具已由 Maven 改為 Gradle Wrapper。離線部署仍以 Maven repository layout 提供依賴來源，實際流程請參考 `doc/java-gradle-offline-build.md`。

本文件基於以下已確認前提提出技術選型建議：

- 目標是將專案中所有 Python 實作逐步改為 Java
- 需支援離線環境部署
- **保留 DuckDB**
- 優先保留現有 SQL 資產與目錄結構

---

## 1. 選型原則

本次選型優先級如下：

1. **與現有 DuckDB/SQL 相容**
2. **可離線部署**
3. **單機安裝與維運成本低**
4. **可取代目前 Python ETL / API / UI**
5. **後續可分階段重構，不必一次重做全部**

---

## 2. 替代方案總覽

### 2.1 方案 A: Spring Boot + Thymeleaf/HTMX

這是我最推薦的方案。

#### 適用範圍

- 取代 `api_server.py`
- 取代 `fsap-month-report-db.py`
- 可與 Java CLI 共用同一套 service / repository / DuckDB 存取層

#### 優點

- 全 Java 技術棧，部署單純
- 後端與頁面可一起打包進單一 `jar`
- 很適合離線環境
- 不需要額外 Node.js 前端建置鏈
- 對內部管理工具、查詢工具、報表工具很實用
- 可快速重做：
  - 查詢頁
  - 匯出頁
  - SQL 歷史頁
  - Schema 檢視頁

#### 缺點

- UI 彈性不如 React/Vue
- 若要做很重的互動式前端，後續還是可能再拆前後端

#### 結論

若目標是 **穩定、可交付、離線友善**，這是第一選擇。

---

### 2.2 方案 B: Spring Boot + React/Vue

這是偏產品化、偏前端體驗導向的方案。

#### 適用範圍

- 查詢工具需要更強互動
- 未來有機會變成正式內部產品
- 願意承擔較高建置與維運成本

#### 優點

- UI 彈性最高
- 可做比 Streamlit 更完整的操作體驗
- 前後端職責更清楚

#### 缺點

- 建置鏈更重
- 離線部署更麻煩
- 需額外管理 Node.js 依賴
- 若只是內部 SQL 查詢與報表工具，通常有點過度設計

#### 結論

若你明確要把它做成長期產品，可以考慮；若目標是先完成 Java 化，不建議作為第一階段方案。

---

### 2.3 方案 C: JavaFX

這是桌面工具導向方案。

#### 適用範圍

- 不希望透過瀏覽器操作
- 明確要做成單機桌面應用程式

#### 優點

- 完全本機化
- 不依賴瀏覽器
- 離線單機情境可行

#### 缺點

- 開發與維護成本高於 Web
- 生態與維護人才較少
- 對 SQL 查詢/報表管理這種需求，通常不如 Web 方案直接

#### 結論

除非你明確要桌面 App，否則不建議。

---

## 3. 推薦主線方案

### 3.1 總體建議

建議採用：

- **Java CLI + Spring Boot + Thymeleaf/HTMX + DuckDB JDBC**

這條路線可同時覆蓋：

- ETL
- 報表輸出
- View 同步
- Query API
- 離線 Web UI

### 3.2 為什麼這條路最適合現在的專案

因為你現在這個專案的核心不是前端互動，而是：

- Excel -> JSONL.gz 的匯入流程
- DuckDB 上的 SQL 資產
- Excel / CSV 報表輸出
- 一個內部用查詢介面

這些需求用 `Spring Boot + Thymeleaf/HTMX` 足以承接，而且部署明顯比 SPA 前端簡單。

---

## 4. 元件級技術建議

### 4.1 CLI 框架

建議：

- `picocli`

用途：

- `ingest`
- `sync-views`
- `generate-report`
- `update-monitor-data`
- `serve`

原因：

- Java CLI 生態成熟
- 參數定義清楚
- 很適合把目前多支 Python 腳本收斂為單一入口命令

---

### 4.2 Web Framework

建議：

- `Spring Boot`

用途：

- REST API
- Web UI
- 靜態資源打包
- 設定管理
- 健康檢查端點

原因：

- 生態完整
- 文件與維護資源最多
- 離線部署常見
- 可在單一程序內同時提供 API 與頁面

---

### 4.3 資料庫存取

建議：

- `DuckDB JDBC`

用途：

- 執行現有 `views/*.sql`
- 執行現有 `reports/*.sql`
- 執行查詢頁 SQL

原因：

- 與現有資料引擎一致
- 風險最低
- 不必重寫 SQL 方言

補充：

- 建議固定 DuckDB 版本，避免未來 SQL 行為漂移

---

### 4.4 Excel 讀寫

建議：

- `Apache POI`

用途：

- 讀取 `01_excel_input/*.xlsx`
- 產出彙總 Excel 報表
- 保留欄寬調整等格式能力

原因：

- Java 生態最成熟的 Excel 函式庫
- 足以取代 `openpyxl`

---

### 4.5 JSON / JSONL / GZIP

建議：

- `Jackson`
- Java 內建 `GZIPOutputStream`

用途：

- 輸出 `.jsonl.gz`
- 寫設定檔 / 歷史檔
- 處理 API JSON

原因：

- Jackson 穩定、標準、與 Spring Boot 整合良好

---

### 4.6 CSV 輸出

建議：

- `Apache Commons CSV`

用途：

- 報表 CSV
- 監控資料輸出
- 需要控制 BOM / encoding 時的輸出流程

---

### 4.7 Log

建議：

- `SLF4J + Logback`

用途：

- 對應目前 `logs/*.log`
- CLI 與 Web 共用 logging 配置

原因：

- Java 標準作法
- 易於控制檔案輸出

---

### 4.8 建置工具

建議：

- `Maven`

原因：

- 企業內部環境通常接受度更高
- 對離線倉庫、依賴鎖定、fat jar 打包都很成熟
- 對 Java-only 專案夠用且穩定

若團隊已熟悉 `Gradle` 也可，但在目前需求下 `Maven` 更保守。

---

## 5. 對現有 Python 腳本的替代映射

| 現有腳本 | Java 替代形態 | 建議技術 |
| :--- | :--- | :--- |
| `step1_ingest.py` | CLI `ingest` | `picocli` + `Apache POI` + `Jackson` + `GZIPOutputStream` |
| `update_views_to_db.py` | CLI `sync-views` | `picocli` + `DuckDB JDBC` |
| `step2_report.py` | CLI `generate-report` | `picocli` + `DuckDB JDBC` + `Apache POI` + `Commons CSV` |
| `api_server.py` | REST API | `Spring Boot Web` |
| `fsap-month-report-db.py` | Web UI | `Spring Boot` + `Thymeleaf/HTMX` |
| `update_monitor_data.py` | CLI `update-monitor-data` | `picocli` + `DuckDB JDBC` + `Commons CSV` |
| `start-fsap-month-report-db.sh` | 啟動腳本 / `serve` 命令 | `java -jar` + `.sh`/`.bat` |

---

## 6. UI 替代方案建議

### 6.1 建議保留的功能

若要取代 `Streamlit`，建議第一版保留：

1. 多分頁 SQL 查詢
2. 查詢結果表格顯示
3. CSV / JSONL 下載
4. TSV 複製區塊
5. Schema 瀏覽
6. 查詢歷史

### 6.2 第一版不必追求的功能

以下可延後：

- 太多前端互動特效
- 進階視覺化圖表
- 即時多人協作
- 複雜權限系統

重點應放在先做出可用、可替代、可離線交付的版本。

---

## 7. 離線部署建議

建議交付內容：

1. `app.jar`
2. 固定版本 `JDK` 或最少 `JRE`
3. `config/`
4. `03_sql_logic/`
5. 啟動腳本：
   - `start.sh`
   - `start.bat`
6. 範例資料與驗證腳本

### 7.1 離線部署原則

- 不依賴執行時下載任何套件
- 不依賴 Python
- 不依賴 Node.js
- 不依賴 Docker registry
- 不依賴外部 CDN

### 7.2 打包形式建議

第一版建議：

- 單一 `fat jar`
- 搭配 `config/` 與專案目錄
- 再加平台啟動腳本

這比多程序、多容器方案更適合目前專案。

---

## 8. 我建議的初版專案結構

```text
fsap-monitor-util/
├── pom.xml
├── src/
│   ├── main/java/... 
│   ├── main/resources/
│   │   ├── templates/
│   │   ├── static/
│   │   └── application.yml
│   └── test/java/...
├── config/
├── 01_excel_input/
├── 02_source_lake/
├── 03_sql_logic/
├── 04_report_output/
├── 05_database/
└── logs/
```

Java 程式碼與既有資料/SQL/輸出目錄分離，會比把所有東西繼續塞在 `scripts/` 更乾淨。

---

## 9. 最終建議

若以現在的需求來看，建議直接定案為：

1. **保留 DuckDB**
2. **以 Maven 建 Java 專案**
3. **CLI 採用 picocli**
4. **Web/API 採用 Spring Boot**
5. **UI 採用 Thymeleaf/HTMX**
6. **Excel 採用 Apache POI**
7. **JSON 採用 Jackson**
8. **CSV 採用 Apache Commons CSV**
9. **Logging 採用 SLF4J + Logback**

這套組合最符合：

- 離線部署
- 低風險移植
- 可保留 DuckDB SQL 資產
- 可逐步替換 Python

---

## 10. 落地結果與後續版本

本文件中的技術選型已完成落地，對應成果如下：

- `Java CLI + Spring Boot + Thymeleaf/HTMX + DuckDB JDBC` 已實作
- `Maven` 單模組骨架已建立
- `ingest`、`sync-views`、`generate-report`、`update-monitor-data`、`serve`、`doctor` 已可用

後續版本規劃建議如下：

- `v1.1`：補 Web UI 任務操作頁，讓目前 CLI 命令可直接由頁面觸發
- `v1.2+`：再補齊多 Tab 查詢、查詢槽位保存、結果匯出 UI、schema 明細等進階互動體驗

---

*文件更新於: 2026-04-22*
