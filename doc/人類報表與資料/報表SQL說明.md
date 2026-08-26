# FSAP 維運報表 SQL 邏輯說明

本文件詳細記錄 `03_sql_logic/reports/` 目錄下所有用於產出維運月報的 SQL 邏輯、參數設定及報表功能。

---

## 🖥️ 1. 資源監控 (CPU/Memory)

| 檔案名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| **`1.1.CPU-MEM監控.sql`** | 產出當月主地與異地機房的使用率。 | 依 IP 分類，計算整個區間的 CPU 與 Memory 平均值。 |
| **`1.1.1. CPU-MEM監控-每日列表.sql`** | 產出當月每日資源明細。 | 依日期與機房類別 Group By，計算各項指標 (AVG, MAX, MIN)。 |

---

## 📊 2. 當月交易量統計 (Monthly Base)

| 檔案名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| **`1.交易量統計-當月.sql`** | 彙整當月概況。 | 結合 `FSAP_Daily` (計算日總量) 與 `FSAP_PeakHour` (定位峰時)。 |
| **`2.交易量統計-當月每日.sql`** | 每日交易量縱向列表。 | 自 `v_rt_pr_hh24_clean` 依日期加總 `tx_cnt`，並排除測試代碼。 |
| **`2.1.交易量統計-當月每日-Pivot.sql`** | 每日交易量橫向報表。 | 先以 `v_rt_pr_hh24_clean` 算出每日總量，再透過 `PIVOT` 將日期轉成橫向欄位。 |

---

## 🚀 3. 峰值分析 (Peak Analysis)

| 檔案名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| **`3.交易量統計-峰日峰時.sql`** | 峰日當天 24 小時分佈。 | 直接用 `v_rt_pr_hh24_clean.tx_cnt` 聚合每日交易量定位峰日，再統計該日小時明細。 |
| **`3.1.交易量統計-峰日峰時-Pivot.sql`** | 峰日當天小時分佈橫向表。 | 峰日判定與小時展開都使用 `v_rt_pr_hh24_clean`。 |

---

## 📈 4. 歷史趨勢比較 (Historical Trends)

| 檔案名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| **`4` 系列 (月總量)** | 近半年每月總量比較。 | 以 `v_rt_pr_hh24_clean.tx_cnt` 做月總量，再用 `LAG()` 算月差異。 |
| **`5` 系列 (單日峰值)** | 每月最高峰日交易量變化。 | 以 `v_rt_pr_hh24_clean.tx_cnt` 聚合每日總量找每月峰日，再計算跨月差異。 |
| **`6` 系列 (歷史峰日峰時)** | 每月最高峰時段變化。 | 先用 `v_rt_pr_hh24_clean.tx_cnt` 找峰日，再用同一小時資料找峰時與時交易量。 |

---

## 🏆 5. 交易類別排行榜 (TOP 6 & Details)

| 檔案名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| **`7` 系列 (排行)** | 交易類/查詢類 Top 6。 | 以 `v_rt_pr_hh24_clean.tx_cnt` 計算交易量，並以小時處理時間加權出每日 / 全月平均，再結合 `v_pr_info` 算佔比。 |
| **`8.交易量統計-所有交易類別-詳細資訊.sql`** | 最詳盡的區間統計。 | 以 `v_rt_pr_hh24_clean` 聚合交易量與處理時間；峰日代表「每日平均處理時間最高日」，峰時代表整段區間內小時交易量最高時段。 |
| **`8.1.交易量統計-月度比較-所有交易類別-詳細資訊.sql`** | 前一月與本月詳細比較。 | 使用 `previousRangeStartTime` / `previousRangeEndTime` 與 `rangeStartTime` / `rangeEndTime` 比較兩個月份的交易總量、峰日、峰時、平均處理時間與佔比。 |
| **`8.2.交易量統計-月度比較-所有交易類別-簡易比較.sql`** | 前一月與本月簡易比較。 | 聚焦交易總量、交易量佔比、筆數變化與變化率。 |
| **`8.3.交易量統計-MOM交易量比較.sql`** | MOM 交易量比較。 | 直接使用 `v_monthly_transaction_stats`，以 `previousTargetMonth` 與 `targetMonth` 比較前後兩月。 |

---

## 🔎 報表運算邏輯詳解：峰日峰時 (`3.交易量統計-峰日峰時.sql`)

這支報表專注於分析「當月交易最繁忙的一天」內，交易量的 24 小時分佈情況：

1.  **動態定位峰日 (`peak_day_select`)**：
    *   先以 `v_rt_pr_hh24_clean` 的 `tx_cnt` 聚合每日總量，再依 `SUM(tx_cnt) DESC LIMIT 1` 自動找出交易量最大的日期。
    *   這代表目前「峰日」判定使用小時明細加總後的日總量。
2.  **小時級展開 (主查詢)**：
    *   以步驟 1 找到的日期為過濾條件 (`WHERE tx_dt_str = (SELECT ... FROM peak_day_select)`)。
    *   小時明細本身仍從 `v_rt_pr_hh24_clean` 讀取，並依小時 (`tx_hour`) 分組。
3.  **格式化呈現**：
    *   使用 `LPAD(..., 2, '0') || ':00'` 將小時數字格式化為 `00:00` 樣式，方便儀表板直接繪製趨勢圖。

---

## 🔎 報表運算邏輯詳解：歷史峰日峰時 (`6.交易量統計-歷史峰日峰時.sql`)
...
這支報表是月報中最複雜的邏輯之一，採用了多層級遞進（Multi-Level Aggregation）架構：

1.  **分層聚合 (Steps 1-4)**：
    *   **日總量聚合** (`FSAP_Daily`)：由 `v_rt_pr_hh24_clean` 依「年月」與「交易日」加總 `tx_cnt` 計算每日總交易量。
    *   **定位月峰日** (`FSAP_MonthlyPeakDay`)：利用 `ROW_NUMBER()` 找出每個月交易量最高的那一天。
    *   **峰日小時明細** (`FSAP_PeakDayHourly`)：只針對上述找到的「峰日」，再去 `v_rt_pr_hh24_clean` 查詢當天每小時 (`tx_hour`) 的交易明細。
    *   **定位日峰時** (`FSAP_PeakHourRank`)：在峰日當天，再次利用 `ROW_NUMBER()` 找出交易量最高的那一個小時。
2.  **趨勢計算 (Steps 5-6)**：
    *   **計算差異** (`CalcDiff`)：使用視窗函數 `LAG()` 取得「上個月的峰時交易量」，並計算本月與上月的增減百分比。
3.  **格式化輸出 (Step 7)**：
    *   將所有計算出的指標轉為最終報表格式，並以日期排序。

---

## 🚀 數據底層與計算口徑說明

目前交易量相關報表主要回到同一個小時資料口徑：

- **交易量 / 峰日 / 峰時 / 排行口徑**
  - 主要使用 `v_rt_pr_hh24_clean`
  - 主要欄位為 `tx_cnt`
  - 適用於：`1`、`2`、`2.1`、`3`、`3.1`、`4`、`4.1`、`5`、`5.1`、`6`、`6.1`、`7`、`7.1`、`7.2`、`8`、`8.1`、`8.2`

- **平均處理時間口徑**
  - 主要使用 `v_rt_pr_hh24_clean`
  - 主要欄位為 `avg_tm_ms`
  - 適用於：`7`、`7.1`、`7.2`、`8`、`8.1`

- **整月交易統計 View**
  - `v_monthly_transaction_stats` 是第 8 頁整月統計的 view 化版本，供 MOM 類報表重用
  - 峰日定義與第 8 頁一致：每日平均處理時間最高日
  - 峰時定義為整月內 `tx_cnt` 最高的小時；若交易量相同，取較早日期、較早小時
  - 目前適用於：`8.3`

目前 SQL 的交易量總數以 `RT_PR_HH24` 的 `tx_cnt` 加總；峰日與峰時以同一份小時資料聚合；`7` 系列平均處理時間使用 `RT_PR_HH24.avg_tm_ms` 搭配 `tx_cnt` 加權。

---

## 參數調整指南

報表 SQL 內保留 `${...}` placeholder，但目前由 `ReportGenerationService` 在執行前代入，**不應為了單次產報直接修改 SQL 檔**。請透過 `generate-report` 的 CLI 參數或 Web UI 設定；每次產出會保存解析後的 `report-params.json` 供追溯。

| Placeholder | 輸入來源 | 影響報表 |
| :--- | :--- | :--- |
| `${targetMonth}` | UI/CLI `--target-month`；未指定時為上個月 | `1`、`2`、`2.1`、`3`、`3.1`、`7`、`7.1`、`7.2`、`8`、`8.3` |
| `${rangeStartDate}` / `${rangeEndDate}` | CLI/API 可覆寫；未指定時為目標月首日與末日 | `1.1`、`1.1.1` |
| `${historyStartMonth}` / `${historyEndMonth}` | UI/CLI 設定；未指定時為 `2025-09` 至目標月 | `4`、`4.1`、`5`、`5.1`、`6`、`6.1` |
| `${rangeStartTime}` / `${rangeEndTime}` | CLI/API 可覆寫；未指定時為目標月 `00:00` 至 `23:59` | `8.1`、`8.2` |
| `${previousTargetMonth}` / `${previousRangeStartTime}` / `${previousRangeEndTime}` | 由 `targetMonth` 自動推導前一個月 | `8.1`、`8.2`、`8.3` |

UI 目前只提供 `targetMonth`、`historyStartMonth`、`historyEndMonth`；日期與時間區間由後端推導。`7.1` 與 `7.2` 的交易類別分別固定在 SQL 中為 `update` 與 `query`，尚未開放為 UI/CLI 參數。

---
## 目前交易量口徑摘要

目前 SQL 的實質口徑如下：

1. `2`、`2.1`
   - 每日交易量使用 `v_rt_pr_hh24_clean.tx_cnt`
2. `3`、`3.1`
   - 峰日判定使用 `v_rt_pr_hh24_clean.tx_cnt`
   - 峰日小時明細使用 `v_rt_pr_hh24_clean.tx_cnt`
3. `4`、`4.1`
   - 歷史月總量使用 `v_rt_pr_hh24_clean.tx_cnt`
4. `5`、`5.1`
   - 歷史峰日使用 `v_rt_pr_hh24_clean.tx_cnt`
5. `6`、`6.1`
   - 峰日判定與峰時分佈都使用 `v_rt_pr_hh24_clean.tx_cnt`
6. `7`、`7.1`、`7.2`
   - 交易量使用 `v_rt_pr_hh24_clean.tx_cnt`
   - 平均處理時間使用 `v_rt_pr_hh24_clean.avg_tm_ms` 依 `tx_cnt` 加權
7. `8`
   - 峰日使用每日平均處理時間最高日
   - 峰時使用整段區間內最高小時交易量，同量時取較早時間
8. `8.3`
   - 使用 `v_monthly_transaction_stats` 比較 `previousTargetMonth` 與 `targetMonth`

若後續你要再調整交易量公式，建議先從這一節回頭判斷：

- 是否要再次把「日總量」切到 `RT_CNT`
- 是否要維持以小時資料 `RT_PR_HH24` 作為單一口徑
- 平均處理時間是否要用 `RT_PR_HH24` 小時加權，或改用 `RT_TMSPT` 日平均

*最後更新日期: 2026-08-26*
