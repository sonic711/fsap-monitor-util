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

業務分類來自 `<base-dir>/02_source_lake/PR_CFG/`。**快照不在目前 Git 追蹤內容中**；新 clone 後，請先匯入含有效 PR_CFG 的每日 Excel，或取得可信交付所附快照。舊交付可能包含 `PR_CFG-20261022.jsonl.gz`，但不能假設原始碼一定附帶。

view 選所有檔案中 `_dt` 最新的一份快照，再依 PR_ID 的匯入時間去重。最新快照每筆 PR_TPKIND 都須有效（1 或 2），否則產報中止；匯入較舊快照不會改變目前分類。重跑歷史月報也使用最新分類，不依目標月份回溯。

完全沒有 PR_CFG 檔時，同步會因 No files found 失敗。補上來源並重新 ingest／sync-views；快照未被 Git 追蹤，不能只靠 git restore 還原它。

- 要找某分頁的 SQL：[報表SQL說明](報表SQL說明.md)。
- 要找 view 的資料來源：[ViewSQL說明](ViewSQL說明.md)。
- 要核對數值算法：[報表公式與口徑](報表公式與口徑.md)。
