# Source Lake

此目錄由 Java `ingest` 產生。每個來源 sheet 會輸出為 `<SHEET>/<SHEET>-YYYYMMDD.jsonl.gz`，並包含原始欄位與 `_file`、`_sheet`、`_dt`、`_ingest_ts` 追溯欄位。

DuckDB views 直接讀取這些檔案。需要重建時請使用 `ingest --force`，不要手動修改 JSONL.GZ。
