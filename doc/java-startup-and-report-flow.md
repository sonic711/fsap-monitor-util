# Java 版啟動服務與產生報告流程說明

本文件說明目前 Java 版 `fsap-monitor-util` 的實際操作流程，從啟動、檢查環境，到最終產生報告與監控輸出。

更新日期：2026-06-01

## 1. 前置條件

### 執行環境

- Java 17
- Gradle Wrapper

### 專案資料目錄

目前 Java 版預設是透過 `--fsap.paths.base-dir` 指向實際資料根目錄，例如：

```bash
fsap-month-report-develop
```

該目錄下應包含：

- `01_excel_input`
- `02_source_lake`
- `03_sql_logic`
- `04_report_output`
- `05_database`
- `logs`

## 2. 建置 Java 專案

在 repo 根目錄執行：

```bash
./gradlew clean bootJar -x test
```

建置完成後，執行檔位置為：

```bash
build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar
```

若要準備離線環境打包用的 Maven repository，請參考 `doc/java-gradle-offline-build.md`。

## 3. 查看可用命令

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar --help
```

目前可用命令：

- `doctor`
- `download-input`
- `ingest`
- `sync-views`
- `generate-report`
- `upload-report`
- `update-monitor-data`
- `serve`

## 4. 外部設定檔

JAR 內仍保留基本預設值，但正式執行時建議把可調整或環境相關設定放在 JAR 外部：

```text
fsap-monitor-util-0.1.0-SNAPSHOT.jar
config/application.yml
config/monitor-data.json
```

程式啟動時會自動讀取下列位置的外部設定，Web `serve` 與所有 command 都共用同一套規則：

- 目前工作目錄的 `application.yml`
- 目前工作目錄的 `config/application.yml`
- JAR 所在目錄的 `application.yml`
- JAR 所在目錄的 `config/application.yml`

因此一般情境可以直接把 `config/application.yml` 放在 JAR 旁邊，不需要每次加 `--spring.config.location`：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar doctor
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar serve --port 18080
```

若仍要手動指定設定檔，Spring 參數要放在 command 前面：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:/app/fsap-monitor-util/config/ \
  doctor
```

## 5. 建議的完整報表產出流程

如果你的目標是從原始 Excel 走到最終報表，建議依下列順序執行。

### Step 0. 從 SFTP 下載每日 Excel（需要時）

如果 `01_excel_input` 尚未有當日來源檔，可先從 SFTP 備份目錄下載：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  download-input --date 20260526
```

用途：

- 搜尋 `/FSAP/FILE_BCKP` 底下包含 `FSAP每日交易統計yyyyMMdd.xlsx` 的備份子目錄
- 下載到 `01_excel_input`
- 寫入 `logs/latest-sftp-download.json`，供後續 `upload-report` 預設上傳回同一個 SFTP 目錄

### Step 1. 檢查環境

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  doctor
```

用途：

- 檢查資料根目錄是否存在
- 檢查 `01_excel_input`、`03_sql_logic`、`05_database` 等必要路徑
- 檢查 DuckDB 是否可正常連線

如果這一步失敗，建議先修正資料目錄或設定，不要直接往下跑。

### Step 2. 將 Excel 匯入 Source Lake

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  ingest
```

用途：

- 讀取 `01_excel_input/*.xlsx`
- 將目標 sheets 轉成 `02_source_lake/**/*.jsonl.gz`

常用參數：

只處理單一天：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  ingest --date 20260421
```

強制重建：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  ingest --force
```

只處理最新 N 個檔案：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  ingest --limit 3
```

### Step 3. 同步 SQL Views 到 DuckDB

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  sync-views
```

用途：

- 讀取 `03_sql_logic/views/*.sql`
- 將 view 定義載入 DuckDB
- 自動重試處理相依 view 順序

可選參數：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  sync-views --max-rounds 5
```

### Step 4. 產生月報表

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report
```

用途：

- 載入 `03_sql_logic/reports/*.sql`
- 執行所有報表 SQL
- 產報前會同步 `03_sql_logic/views/*.sql`，因此 `8.3` 可直接使用 `v_monthly_transaction_stats`
- 輸出 Excel 與各項 CSV

輸出位置：

```bash
04_report_output/YYYYMMDDHHMM/
```

例如：

```bash
04_report_output/202604211930/
```

該批次目錄內通常會有：

- `維運月度報表_<報表月份>彙總_YYYYMMDDHHMM.xlsx`，例如 `targetMonth=2026-04` 時為 `維運月度報表_04月彙總_YYYYMMDDHHMM.xlsx`
- 各報表對應的 `.csv`

如果你要指定批次時間戳：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report --timestamp 202604211930
```

如果你要允許單支 SQL 失敗時繼續跑剩餘報表：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report --continue-on-error
```

### Step 5. 上傳月報表到 SFTP

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  upload-report
```

用途：

- 預設尋找 `04_report_output/<最新批次>/` 底下最新的 `.xlsx` 報表
- 讀取 `logs/latest-sftp-download.json`
- 將報表上傳到最近一次 `download-input` 找到原始 Excel 的同一個 SFTP 目錄

完整主線流程中，`download-input` 成功後會記錄最近一次下載來源，例如：

```json
{
  "filename": "FSAP每日交易統計20260526.xlsx",
  "remotePath": "/FSAP/FILE_BCKP/01150526/FSAP每日交易統計20260526.xlsx",
  "remoteDirectory": "/FSAP/FILE_BCKP/01150526",
  "localPath": ".../01_excel_input/FSAP每日交易統計20260526.xlsx",
  "matchedDirectory": "01150526",
  "downloadedAt": "2026-05-26 10:00:00"
}
```

因此報表會上傳到同一個 `remoteDirectory`，例如：

```text
/FSAP/FILE_BCKP/01150526/維運月度報表_05月彙總_202606010930.xlsx
```

如果要手動指定檔案或目錄：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  upload-report \
  --local-file 04_report_output/202606010930/維運月度報表_05月彙總_202606010930.xlsx \
  --remote-dir /FSAP/FILE_BCKP/01150526
```

如果遠端已有同名報表，預設會停止；需要覆蓋時加上：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  upload-report --overwrite
```

## 6. 監控資料輸出流程

如果除了報表外，還要產生 dashboard 用的監控資料：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  update-monitor-data
```

用途：

- 讀取 `config/monitor-data.json`
- 查詢監控相關 views
- 輸出 `CSV + JS`

輸出位置：

```bash
04_report_output/monitor-data/
```

典型產物：

- `daily_data.csv`
- `daily_data.js`
- `hourly_data.csv`
- `hourly_data.js`
- `jvm_daily_data.csv`
- `jvm_daily_data.js`
- `jvm_hourly_data.csv`
- `jvm_hourly_data.js`

如果你要指定其他 config：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  update-monitor-data --config config/monitor-data.json
```

## 7. 啟動 Web Service

如果你要用 Java Web Dashboard 查詢資料或下載產物：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  serve
```

目前 Web UI 預設為可操作模式，亦即可直接在頁面上執行：

- `doctor`
- `sync-views`
- `ingest`
- `generate-report`
- `update-monitor-data`

預設 port：

```bash
8080
```

如果要指定 port：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  serve --port 18080
```

如果你要明確指定可操作模式：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  serve --writable
```

如果你只想開查詢與下載，不允許從 UI 觸發任務：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  serve --readonly
```

啟動後可使用：

- 首頁：`http://127.0.0.1:8080/`
- 健康檢查：`http://127.0.0.1:8080/health`
- 查詢 API：`POST http://127.0.0.1:8080/api/query`
- Schema API：`GET http://127.0.0.1:8080/api/schema`

### v1.1 Web UI 結構

目前首頁已分成三個 tab：

1. `Operations`
2. `Monitor Dashboard`
3. `Query Console`

`Operations` 內包含：

- `Operations` 任務操作區
- `Recent Report Batches`
- `Task Status`

`Monitor Dashboard` 內包含：

- `Monitor Data Exports`
- monitor CSV 互動表格
- Chart.js 趨勢圖

`Query Console` 內包含：

- `SQL Query`
- `Recent Query History`
- `Schema Snapshot`

### v1.1 UI 操作流程

若你希望透過頁面完成匯入與報表流程，可依序操作：

1. 進入 `Operations`
2. 若有新的 Excel，先在 `Ingest` 區塊上傳 `.xlsx`
3. 執行 `Doctor`
4. 執行 `Ingest`
5. 執行 `Sync Views`
6. 執行 `Generate Report`
7. 如需監控資料，再執行 `Update Monitor Data`

### v1.1 Workflow 鎖定

目前 UI 與後端 API 都會對主線任務套用正式順序：

1. `Doctor`
2. `Ingest`
3. `Sync Views`
4. `Generate Report`

這代表：

- UI 會以 4 步 workflow 卡片顯示順序
- 尚未到的步驟會顯示 `LOCKED`
- 即使直接呼叫 API，也不能跳步執行
- 若重新成功執行前面的步驟，後面的步驟狀態會重新回到待執行

例如：

- 未完成 `Doctor` 前，不能執行 `Ingest`
- 未完成 `Ingest` 前，不能執行 `Sync Views`
- 未完成 `Sync Views` 前，不能執行 `Generate Report`

補充：

- `Ingest` 區塊會顯示目前 `01_excel_input` 內已有的 `.xlsx` 檔案
- `Recent Report Batches` 已提供 `Refresh` 按鈕，可在產生報表後局部刷新
- `Task Status` 會顯示最近由 UI 觸發的任務結果
- UI 任務歷史目前只保留在記憶體中，若重啟服務會清空
- `Update Monitor Data` 目前不在這條主線 workflow 內，視為額外操作

## 8. 建議實際操作順序

如果你是第一次驗證 Java 版，建議照這個順序：

1. `./gradlew clean bootJar -x test`
2. `download-input`（若需要從 SFTP 抓來源檔）
3. `doctor`
4. `ingest`
5. `sync-views`
6. `generate-report`
7. `upload-report`（若需要回傳月報到 SFTP）
8. `update-monitor-data`
9. `serve`

## 9. 最終產出整理

### 報表產物

位置：

```bash
04_report_output/YYYYMMDDHHMM/
```

內容：

- 1 份彙總 Excel
- 多份報表 CSV

### 監控產物

位置：

```bash
04_report_output/monitor-data/
```

內容：

- 監控 CSV
- 前端使用的 JS 包裝檔

### 執行紀錄

位置：

```bash
logs/
```

常見檔案：

- `ingest.log`
- `report_execution.log`
- `query_history.log.jsonl`

## 10. 一套最短可用流程範例

如果你只是要快速跑出 Java 版月報，可直接使用：

```bash
./gradlew clean bootJar -x test

java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  doctor

java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  ingest

java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  sync-views

java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report
```

如果還要產生監控資料與啟動 web：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  update-monitor-data

java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  serve --port 18080
```

## 11. 結論

目前 Java 版已可支撐以下主線：

- 啟動服務
- 匯入 Excel
- 同步 SQL views
- 產生報表
- 產生監控資料
- 提供離線 web 查詢與下載入口

如果你接下來要做人工驗證，建議直接以本文件第 7 節的順序逐步操作。
