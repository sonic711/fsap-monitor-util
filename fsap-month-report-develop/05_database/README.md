# DuckDB Database

預設 DuckDB 檔案為 `fsap-month-report.duckdb`。Java `sync-views` 會重建 `03_sql_logic/views/` 中的 view；`generate-report` 與 `update-monitor-data` 也會在執行前同步 views。

此檔案是執行期資料，更新 SQL 或重新 ingest 後應透過 CLI 重建 view，而非手動修改資料庫定義。
