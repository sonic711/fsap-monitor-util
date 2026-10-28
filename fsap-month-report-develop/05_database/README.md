# DuckDB 資料庫目錄

預設 DuckDB 檔案為 `fsap-month-report.duckdb`。Java `sync-views` 會重建 `03_sql_logic/views/` 中的 view；`generate-report` 與 `update-monitor-data` 也會在執行前同步 views。

此檔案是執行期資料，更新 SQL 或重新 ingest 後應透過 CLI 重建 view，而非手動修改資料庫定義。

此目錄不在 Git 內。完整還原需要數據湖、參考資料與 SQL，不只是 .duckdb；備份或替換前先停止 Web／Job 等所有存取，詳見 [DuckDB資料庫](../../doc/人類報表與資料/DuckDB資料庫.md)。
