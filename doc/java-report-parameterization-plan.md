# Java 報表日期參數化與 UI/CLI 共用方案

更新日期：2026-04-27

## 0. 目前狀態

此方案已完成第一版實作，包含：

- `Generate Report` UI 表單目前只保留月份欄位：
  - `Reporting Month`
  - `History Month Start`
  - `History Month End`
- UI 不直接編輯日期與時間區間，改由後端依 `Reporting Month` 自動推導
- CLI `generate-report` 已支援同語意參數
- `ReportGenerationService` 已改為吃共用 `ReportGenerationRequest`
- `reports/*.sql` 已改為 placeholder render
- 每次批次輸出會額外保存 `report-params.json`

目前第一版尚未把 `targetCategory` 暴露成 UI/CLI 參數，交易類別仍沿用各 SQL 既有定義。

## 1. 問題背景

目前 `03_sql_logic/reports/*.sql` 內有多份 SQL 直接把月份或日期區間寫死，例如：

- `target_month = '2026-04'`
- `start_date = '2026-04-01'`
- `end_date = '2026-04-30'`
- `StartYM = '2025-09'`
- `EndYM = '2026-04'`

這代表：

- 若要改統計月份，必須直接修改 SQL 檔
- Web UI 目前不能調整這些日期參數
- CLI 目前也不能透過 `generate-report` 傳入這些日期參數
- 同一份 SQL 若被臨時改掉，之後容易忘記改回來

## 2. 目標

希望達成：

1. 使用者可在 UI 上調整報表月份，日期區間由系統自動推導
2. CLI 仍可用相同參數執行，不失去指令彈性
3. `reports/*.sql` 不需要每次手動改檔
4. 報表輸出時能保留本次執行參數，方便追溯

## 3. 不建議的做法

### 3.1 直接讓 UI 編輯 SQL 檔

不建議。

原因：

- 會讓 SQL 資產變成執行期被修改的檔案
- 容易留下「這次改了、下次忘了改回來」的狀態
- 難以區分「報表邏輯變更」與「本次執行參數變更」
- 也不利於 CLI 與 UI 共用同一套執行模型

### 3.2 只做 UI 參數，不做 CLI 參數

不建議。

原因：

- 會讓 UI 能做、CLI 不能做
- 自動化腳本、批次任務、離線操作會失去彈性
- 後續維護時會形成兩套執行模式

## 4. 建議方案

建議把這件事做成：

- **SQL 邏輯模板化**
- **UI 與 CLI 共用同一組參數模型**
- **後端在執行前把參數代入 SQL**

也就是：

1. `reports/*.sql` 改成保留 placeholder
2. UI 傳 `report parameters`
3. CLI 也傳相同語意的 `report parameters`
4. `ReportGenerationService` 只吃一個共用 request model
5. 執行時先 render SQL，再跑查詢

## 5. 建議的參數模型

第一版不要做得太散，先收斂成共用欄位。

建議欄位如下：

- `targetMonth`
  - 格式：`YYYY-MM`
  - 用途：當月報表、峰日峰時、交易類別統計

- `rangeStartDate`
  - 格式：`YYYY-MM-DD`
  - 用途：每日 CPU/MEM 監控起日
  - 備註：UI 不直接提供欄位，預設由 `targetMonth` 自動推導

- `rangeEndDate`
  - 格式：`YYYY-MM-DD`
  - 用途：每日 CPU/MEM 監控迄日
  - 備註：UI 不直接提供欄位，預設由 `targetMonth` 自動推導

- `rangeStartTime`
  - 格式：`YYYY-MM-DD HH:mm`
  - 用途：詳細交易報表起始時間
  - 備註：UI 不直接提供欄位，預設由 `targetMonth` 自動推導

- `rangeEndTime`
  - 格式：`YYYY-MM-DD HH:mm`
  - 用途：詳細交易報表結束時間
  - 備註：UI 不直接提供欄位，預設由 `targetMonth` 自動推導

- `historyStartMonth`
  - 格式：`YYYY-MM`
  - 用途：歷史月總量 / 歷史峰日 / 歷史峰日峰時起始月份

- `historyEndMonth`
  - 格式：`YYYY-MM`
  - 用途：歷史月總量 / 歷史峰日 / 歷史峰日峰時結束月份

- `targetCategory`
  - 值域：`all | update | query`
  - 用途：所有交易類別 / TOP6 交易類 / TOP6 查詢類 / 詳細資訊
  - 備註：保留為後續擴充欄位，第一版尚未實作到 UI/CLI

## 5.1 參數與報表分頁對照

目前報表分頁名稱就是 `03_sql_logic/reports/*.sql` 的檔名去掉 `.sql` 後產生，因此可直接用下列對照判斷哪個參數會影響哪一張分頁。

- `targetMonth`
  - 影響分頁：`1.交易量統計-當月`
  - 影響分頁：`2.交易量統計-當月每日`
  - 影響分頁：`2.1.交易量統計-當月每日-Pivot`
  - 影響分頁：`3.交易量統計-峰日峰時`
  - 影響分頁：`3.1.交易量統計-峰日峰時-Pivot`
  - 影響分頁：`7.交易量統計-所有交易類別`
  - 影響分頁：`7.1.交易量統計-TOP6交易類`
  - 影響分頁：`7.2.交易量統計-TOP6查詢類`

- `rangeStartDate` / `rangeEndDate`
  - 影響分頁：`1.1.CPU-MEM監控`
  - 影響分頁：`1.1.1. CPU-MEM監控-每日列表`

- `historyStartMonth` / `historyEndMonth`
  - 影響分頁：`4.交易量統計-歷史月總量`
  - 影響分頁：`4.1.交易量統計-歷史月總量-Pivot`
  - 影響分頁：`5.交易量統計-歷史峰日`
  - 影響分頁：`5.1.交易量統計-歷史峰日-Pivot`
  - 影響分頁：`6.交易量統計-歷史峰日峰時`
  - 影響分頁：`6.1.交易量統計-歷史峰日峰時-Pivot`

- `rangeStartTime` / `rangeEndTime`
  - 影響分頁：`8.交易量統計-所有交易類別-詳細資訊`

## 5.2 預設聯動規則

若使用者沒有把所有欄位都填滿，後端目前會依下列規則補預設值：

- 只填 `targetMonth`：
  - `rangeStartDate` 會補成該月 1 號
  - `rangeEndDate` 會補成該月最後一天
  - `rangeStartTime` 會補成該月 1 號 `00:00`
  - `rangeEndTime` 會補成該月最後一天 `23:59`
  - `historyEndMonth` 會補成 `targetMonth`
  - `historyStartMonth` 會補成 `targetMonth` 往前 7 個月

- 月底天數判斷規則：
  - 後端使用 Java `YearMonth.atEndOfMonth()`
  - 因此會自動判斷 28 / 29 / 30 / 31 天，也會正確處理閏年 2 月
  - UI 只做顯示預覽，真正執行結果仍以後端推導為準

- 若已明確填入 `rangeStartDate` / `rangeEndDate`：
  - `1.1` / `1.1.1` 會以這兩個欄位為準
  - 不會再被 `targetMonth` 覆蓋

- 若已明確填入 `rangeStartTime` / `rangeEndTime`：
  - `8` 分頁會以這兩個欄位為準
  - 不會再被 `targetMonth` 覆蓋

- 若已明確填入 `historyStartMonth` / `historyEndMonth`：
  - `4.x` / `5.x` / `6.x` 會以這兩個欄位為準
  - 不會再被 `targetMonth` 覆蓋

## 6. SQL 寫法建議

目前這種硬編碼：

```sql
WITH params AS (
    SELECT '2026-04' AS target_month
)
```

建議改成：

```sql
WITH params AS (
    SELECT '${targetMonth}' AS target_month
)
```

日期區間同理：

```sql
WITH params AS (
    SELECT
        '${rangeStartDate}' AS start_date,
        '${rangeEndDate}' AS end_date
)
```

歷史月份：

```sql
WITH params AS (
    SELECT
        '${historyStartMonth}' AS StartYM,
        '${historyEndMonth}' AS EndYM
)
```

詳細資訊時間區間：

```sql
WITH params AS (
    SELECT
        '${rangeStartTime}' AS StartTime,
        '${rangeEndTime}' AS EndTime,
        '${targetCategory}' AS TargetCategory
)
```

## 7. 後端實作建議

### 7.1 共用 request model

目前已改成共用 request model，例如：

```text
GenerateReportRequest
- timestamp
- continueOnError
- targetMonth
- rangeStartDate
- rangeEndDate
- rangeStartTime
- rangeEndTime
- historyStartMonth
- historyEndMonth
- targetCategory
```

目前這個 model 已同時給：

- CLI `GenerateReportCommand`
- Web `GenerateReportTaskRequest`
- `ReportGenerationService`

### 7.2 SQL render 層

不建議直接上完整模板引擎。

第一版已採用：

- 白名單 key
- 固定 placeholder 格式：`${key}`
- 嚴格格式驗證
- 字串替換

這樣可以避免：

- UI 任意拼 SQL
- 參數注入不受控
- 模板語法過重

### 7.3 參數驗證

建議後端驗證：

- `targetMonth` 必須符合 `YYYY-MM`
- `rangeStartDate` / `rangeEndDate` 必須符合 `YYYY-MM-DD`
- `rangeStartTime` / `rangeEndTime` 必須符合 `YYYY-MM-DD HH:mm`
- `historyStartMonth <= historyEndMonth`
- `rangeStartDate <= rangeEndDate`
- `rangeStartTime <= rangeEndTime`
- `targetCategory` 只能是白名單值

## 8. UI 設計建議

不要做成「每份報表各自一組參數欄位」。

目前 UI 已收斂成只保留三個月份欄位：

- Reporting Month
- History Month Start
- History Month End

建議 UI 行為：

- 預設帶出最近一次執行參數或合理預設值
- `Date Range` 與 `DateTime Range` 不直接輸入，僅顯示由 `Reporting Month` 推導出的結果
- `Generate Report` 按下時，把這組參數一起送到後端
- 產出完成後，在 batch 目錄保留一份 `report-params.json`

## 9. CLI 設計建議

這個方案不應削弱 CLI，反而應讓 CLI 更強。

建議最終 CLI 形式類似：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report \
  --target-month 2026-04 \
  --history-start-month 2025-09 \
  --history-end-month 2026-04
```

或：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report \
  --range-start-date 2026-04-01 \
  --range-end-date 2026-04-30
```

或：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=fsap-month-report-develop \
  generate-report \
  --range-start-time "2026-04-01 00:00" \
  --range-end-time "2026-04-30 23:59" \
  --target-category update
```

## 10. 建議的第一階段實作範圍

建議先做這些，不要一次把所有報表特殊規則都做成高度動態：

1. 擴充 `GenerateReportTaskRequest`
2. 擴充 `GenerateReportCommand`
3. 新增共用 `GenerateReportRequest`
4. `ReportGenerationService` 新增 SQL placeholder render
5. 先把目前硬編碼日期的 report SQL 改成 placeholder
6. 在輸出批次目錄中保存 `report-params.json`

## 11. 預期影響範圍

主要影響檔案會是：

- `src/main/java/com/fsap/monitor/cli/command/GenerateReportCommand.java`
- `src/main/java/com/fsap/monitor/web/dto/GenerateReportTaskRequest.java`
- `src/main/java/com/fsap/monitor/web/controller/TaskController.java`
- `src/main/java/com/fsap/monitor/core/task/TaskExecutionService.java`
- `src/main/java/com/fsap/monitor/core/report/ReportGenerationService.java`
- `src/main/resources/templates/query.html`
- `fsap-month-report-develop/03_sql_logic/reports/*.sql`

## 12. 結論

建議方案不是「讓 UI 去改 SQL」，而是：

- 保留 SQL 作為報表邏輯資產
- 把月份與日期區間抽成可傳入參數
- 讓 UI 與 CLI 共用同一組 request model

這樣的好處是：

- UI 可操作
- CLI 不失去彈性
- SQL 不再需要為了每次報表去手動改檔
- 每次執行可保留參數紀錄，便於追溯

目前狀態：**本文件僅為建議方案，尚未開始實作。**
