# 邏輯中心目錄說明 (03_sql_logic)

本目錄為專案的「邏輯中心」，統一管理所有報表運算規則、數據視圖定義與業務邏輯 SQL。系統透過這些定義，將原始數據湖中的離散數據轉化為結構化的決策資訊。

---

## 📂 目錄結構說明

```text
03_sql_logic/
├── reports/    # 報表查詢與產出邏輯 (Reporting Layer)
└── views/      # 數據標準化視圖定義 (Semantic Layer)
```

---

## 1. 報表查詢邏輯 (`reports/*.sql`)
本目錄存放的是**直接用於產出最終報表**的查詢語法。

*   **功能定位**：這些 SQL 檔案是報表產出引擎的直接輸入；Java 版由 `ReportGenerationService` 掃描 `reports/*.sql`，旨在將清洗後的數據進行「維度聚合」與「趨勢分析」。
*   **設計原則**：
    *   **參數化設計**：SQL 使用 `${...}` placeholder。Java 版會由 `ReportGenerationService` 依 `generate-report` 的 UI/CLI 參數代入；日常產報不應直接修改 SQL 檔。
    *   **格式標準化**：產出結果通常對應簡報或 Excel 的表格格式，支援透視表 (Pivot) 輸出。
*   **關鍵功能**：涵蓋了交易量統計、資源監控趨勢、峰值分析 (Peak) 以及各類交易排行的邏輯定義。

## 2. 數據標準化視圖 (`views/*.sql`)
本目錄存放的是**資料庫層級的 View 定義**，用於建構語意層 (Semantic Layer)。

*   **功能定位**：這些 SQL 檔案經由視圖同步流程持久化至 DuckDB 資料庫中；Java 版由 `ViewSyncService` 掃描 `views/*.sql` 並建立 view。它們是報表 SQL 的「底層建築」，負責隱藏原始數據的處理細節。
*   **設計原則**：
    *   **數據正規化**：將來自 JSONL 或 CSV 的原始數據轉換為關聯式結構 (TIMESTAMP, DECIMAL, INTEGER)。
    *   **跨年修正**：自動處理原始日誌與檔名時間可能存在的跨年邏輯誤差。
    *   **數據清洗**：透過 `QUALIFY` 語法進行去重 (Deduplication)，確保每個時間點的記錄唯一性。
*   **關鍵功能**：提供如 `v_rt_pr_hh24_clean` (小時級高精度明細) 或 `v_prod_monitor_log` (系統監控清洗) 等標準化介面，確保報表 SQL 無需關心複雜的清洗邏輯。

---

## 🛠 維護與同步機制
1. **異動更新**：修改 `views/` 後請執行 `sync-views`；`generate-report` 與 `update-monitor-data` 也會在執行前同步 views。修改 `reports/` 後不需額外同步，下一次 `generate-report` 會直接讀取最新檔案。
    ```bash
    # Java 版同步視圖
    java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar \
      --fsap.paths.base-dir=fsap-month-report-develop \
      sync-views
    ```
2. **依賴關係**：系統採用自動重試機制，無需擔心視圖之間的引用順序。

---
*最後更新日期: 2026-08-26*
