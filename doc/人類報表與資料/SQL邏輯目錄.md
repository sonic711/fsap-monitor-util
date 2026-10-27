# SQL 邏輯目錄

SQL 位於 `<base-dir>/03_sql_logic/`。

| 子目錄 | 用途 | 修改後如何使用 |
| --- | --- | --- |
| `views/` | 將 JSONL.GZ、CSV 整理成可查詢的 view | 執行 `sync-views` |
| `reports/` | 計算最終報表欄位與數值 | 重新執行 `generate-report` |
| `macros/`（若有） | 產報前載入的共用 SQL 定義 | 重新執行 `generate-report` |

`generate-report` 與 `update-monitor-data` 都會先同步 views。同步會處理依賴順序並重試，但來源缺檔或 SQL 錯誤仍會失敗，需查看執行結果。

日常更換月份請傳 `--target-month` 等參數；SQL 中的 `${...}` 由 Java 代入，不需要手動改檔。修改外部 SQL 不需重新打包 JAR。

## 業務分類預設資料

業務分類來自數據湖 `<base-dir>/02_source_lake/PR_CFG/`。專案附有一份預設快照 `PR_CFG-20261022.jsonl.gz`，讓 `v_cfg_pr_bzkind` 在尚未匯入任何 `PR_CFG` 分頁時也能建立（`sync-views` 可執行）。預設快照取自臺銀 2026-10-22 每日 Excel 的 `PR_CFG`（128 筆，含 `PR_TPKIND`，`_dt=20261022`）。`generate-report` 的前置檢查會要求最新快照每筆都有 1 或 2 的 `PR_TPKIND`（決定交易類別），否則中止並說明原因；之後匯入含 `PR_CFG` 分頁的 Excel：日期較新的快照會自動取代預設快照（view 只取 `_dt` 最新的一份，同一天則取匯入時間較新者），**較新的快照若沒有 `PR_TPKIND` 仍會中止**；日期早於 `20261022` 的快照（例如臺銀 2026-10-22 之前沒有 `PR_TPKIND` 的檔）不會取代它，因此用舊檔重跑時仍使用預設快照的名稱與類別。不需要刪除預設檔。

數據湖完全沒有 `PR_CFG` 檔時，`sync-views` 會因 `v_cfg_pr_bzkind` 的 `No files found` 失敗，所有依賴它的 view 與報表也無法執行；若預設檔被刪除，請從專案還原。

- 要找某分頁的 SQL：[報表SQL說明](報表SQL說明.md)。
- 要找 view 的資料來源：[ViewSQL說明](ViewSQL說明.md)。
- 要核對數值算法：[報表公式與口徑](報表公式與口徑.md)。
