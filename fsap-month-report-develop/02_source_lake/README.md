# 數據湖目錄

此目錄由 Java `ingest` 產生。每個來源 sheet 會輸出為 `<SHEET>/<SHEET>-YYYYMMDD.jsonl.gz`，並包含原始欄位與 `_file`、`_sheet`、`_dt`、`_ingest_ts` 追溯欄位。

DuckDB views 直接讀取這些檔案，交易資料不會先倒入實體 table。需要重建時請使用指定日期的 ingest --force，不要直接修改 JSONL.GZ。

PR_CFG 為報表必要的分類快照，使用最新 _dt 而非報表月份。數據湖與快照未被 Git 追蹤；空白 clone 需要先匯入來源或準備可信快照，不會自動附帶完整歷史資料。

這是實際報表來源，備份不可只保存 DuckDB。詳見 [SQL邏輯目錄](../../doc/人類報表與資料/SQL邏輯目錄.md)。
