# Excel 輸入目錄

放置待匯入的來源 Excel。現行 Java `ingest` 只處理符合 `FSAP每日交易統計YYYYMMDD.xlsx` 的 `.xlsx` 檔案；檔名規則可由 `fsap.ingest.filename-pattern` 覆寫。

Web 上傳或 SFTP 下載後仍須 ingest；更正同名 Excel 之後，以指定日期加 --force 重建。補大量缺檔時不要使用 --limit 3，它只查看最新三份。

指令與資料完整度說明見 [啟動與產生報表](../../doc/人類操作/啟動與產生報表.md)。
