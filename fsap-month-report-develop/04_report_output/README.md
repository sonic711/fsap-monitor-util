# Report Output

`generate-report` 會在 `<timestamp>/` 下輸出彙總 `.xlsx`、每支報表 SQL 的 sidecar `.csv` 與 `report-params.json`。`update-monitor-data` 會另外輸出到 `monitor-data/`，包含各 task 的 `.csv` 與 `.js`。

報表批次與 monitor-data 都是可重新產生的產物；保留策略應依部署環境的稽核與容量需求決定。
