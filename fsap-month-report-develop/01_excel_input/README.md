# Excel Input

放置待匯入的來源 Excel。現行 Java `ingest` 只處理符合 `FSAP每日交易統計YYYYMMDD.xlsx` 的 `.xlsx` 檔案；檔名規則可由 `fsap.ingest.filename-pattern` 覆寫。

請使用 `java -jar ... --fsap.paths.base-dir=fsap-month-report-develop ingest` 轉換資料，不要手動建立 `02_source_lake` 內容。
