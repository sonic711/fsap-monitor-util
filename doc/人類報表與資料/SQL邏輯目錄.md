# SQL 邏輯目錄

SQL 位於 `<base-dir>/03_sql_logic/`。

| 子目錄 | 用途 | 修改後如何使用 |
| --- | --- | --- |
| `views/` | 將 JSONL.GZ、CSV 整理成可查詢的 view | 執行 `sync-views` |
| `reports/` | 計算最終報表欄位與數值 | 重新執行 `generate-report` |
| `macros/`（若有） | 產報前載入的共用 SQL 定義 | 重新執行 `generate-report` |

`generate-report` 與 `update-monitor-data` 都會先同步 views。同步會處理依賴順序並重試，但來源缺檔或 SQL 錯誤仍會失敗，需查看執行結果。

日常更換月份請傳 `--target-month` 等參數；SQL 中的 `${...}` 由 Java 代入，不需要手動改檔。修改外部 SQL 不需重新打包 JAR。

- 要找某分頁的 SQL：[報表SQL說明](報表SQL說明.md)。
- 要找 view 的資料來源：[ViewSQL說明](ViewSQL說明.md)。
- 要核對數值算法：[報表公式與口徑](報表公式與口徑.md)。
