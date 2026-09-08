# DuckDB 資料庫

預設檔案是 `<base-dir>/05_database/fsap-month-report.duckdb`，由 `fsap.paths.database-file` 設定。程式直接開啟檔案，不需要另啟資料庫伺服器。

本專案的 `ingest` 將資料寫成 `02_source_lake/` 下的 JSONL.GZ；`sync-views` 在 DuckDB 建立 view 定義，查詢時再讀取這些外部檔案。因此 **只備份 .duckdb 不足以還原完整報表資料**。

備份時先停止存取相同資料的 Web 與批次程序，正常關閉連線，再保存：

| 內容 | 用途 |
| --- | --- |
| `05_database/` | 資料庫及相關檔案 |
| `02_source_lake/` | 報表實際讀取的交易與監控資料 |
| `00_info/`、`03_sql_logic/` | 參考資料與計算規則 |
| `01_excel_input/` | 需要重建時使用的原始資料 |
| 外部設定檔 | 還原路徑與連線設定 |

還原後執行 `doctor`、`sync-views`，再核對代表性報表。不要在另一個 Job 或 Web 仍存取資料庫時直接搬移或替換 DB。

`-Djava.io.tmpdir` 只調整暫存檔位置，不會變更此資料庫路徑。
