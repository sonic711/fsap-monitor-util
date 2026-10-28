# 報表輸出目錄

generate-report 會建立 12 位時間的批次目錄：

```text
<yyyyMMddHHmm>/
  維運月度報表_MM月彙總_<yyyyMMddHHmm>.xlsx
  report-params.json
  SUM/       ALL 的全部 CSV 與完整彙總 Excel
  FSAP/      該分類的 CSV 與完整彙總 Excel
  ...        其他分類
```

根目錄的新月報為總覽＋各分類的選定分頁，預設 55 頁，亦為 SFTP 預設上傳檔；不再建立 REPORT 目錄。只產 ALL 或停用比較時，根目錄月報改用 ALL 完整彙總。

update-monitor-data 另輸出 monitor-data/*.csv 與 *.js。兩者不是同一條流程。

報表批次與 monitor-data 都是可重新產生的產物；保留策略應依部署環境的稽核與容量需求決定。

資料夾是執行時間而非報表月份；重用時間可能覆寫且保留舊殘檔。看到 Excel 不代表成功，先核對 failure=0。詳見 [報表輸出目錄](../../doc/人類報表與資料/報表輸出目錄.md)。
