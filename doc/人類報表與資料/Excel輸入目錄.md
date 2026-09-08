# Excel 輸入目錄

將每日原始 Excel 直接放在 `<base-dir>/01_excel_input/`。正式主機的預設位置是：

```text
/app/fsap-monitor-util/fsap-month-report-develop/01_excel_input/
```

- 檔名為 `FSAP每日交易統計YYYYMMDD.xlsx`，例如 `FSAP每日交易統計20260801.xlsx`。
- 掃描不包含子目錄；壓縮檔、Excel 鎖定檔與改名後不符合規則的檔案不會匯入。
- 手動放檔、Web 上傳、SFTP 下載完成後，都需要執行 `ingest`。
- `ingest` 會把選定分頁轉成 `02_source_lake/<分頁>/<分頁>-YYYYMMDD.jsonl.gz`。
- 已存在的輸出會跳過；更正同日期 Excel 後，用 `ingest --date YYYYMMDD --force` 重建。
- 補歷史資料不要使用 `--limit 3`，它只檢查最新三份檔案。

原始 Excel 是重建資料的依據，請保留或歸檔。完整補檔指令見 [啟動與產生報表](../人類操作/啟動與產生報表.md)，分頁用途見 [每日交易統計資料說明](每日交易統計資料說明.md)。
