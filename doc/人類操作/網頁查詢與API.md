# 網頁查詢與 API

啟動方式見 [啟動與產生報表](啟動與產生報表.md)。以下假設服務位於 `http://localhost:18080`。

## 網頁能做什麼

| 頁籤 | 現有功能 |
| --- | --- |
| Operations | 依序檢查、匯入、同步 views、產報；上傳來源 Excel；下載批次根目錄成品 |
| Monitor Dashboard | 監控 CSV 表格與趨勢圖，見 [監控資料匯出](監控資料匯出.md) |
| Query Console | 單一 SQL 編輯器、JSON 結果、成功查詢歷史、table／view 名稱與型態清單 |

目前沒有 10 個獨立 SQL 分頁、查詢槽位保存、SQL 結果 CSV／JSONL 匯出或欄位型別瀏覽器；不要混用舊 Streamlit 文件。

## 常用 API

| 方法與路徑 | 用途 |
| --- | --- |
| `GET /health` | 回傳 `{"status":"online"}`，只表示 HTTP 服務存活，不檢查 DB／SFTP／資料完整性 |
| `POST /api/query` | JSON body：`{"sql":"SELECT 1 AS ok"}`；成功回傳 columns、rows、data、count，rows 與 data 為相同結果 |
| `GET /api/schema` | 回傳 main schema 的 table／view 名稱與型態，不含欄位明細 |
| `GET /api/tasks` | 目前任務、近期任務與工作流程狀態 |
| `POST /api/tasks/doctor` | 環境檢查 |
| `POST /api/tasks/ingest` | 參數：force、limit、date |
| `POST /api/tasks/sync-views` | 參數：maxRounds、failFast |
| `POST /api/tasks/generate-report` | 參數：targetMonth、historyStartMonth、historyEndMonth、targetBzkinds 等，日期／時間規則見報表文件 |
| `POST /api/tasks/update-monitor-data` | 可選 configPath |
| `POST /api/tasks/ingest/upload` | multipart 欄位 `files`，上傳後仍須 ingest |
| `GET /api/tasks/ingest/files` | 輸入檔清單 |
| `GET /api/tasks/report-batches` | 最近 3 批報表，每批最多列根目錄 8 個檔案 |
| `GET /api/tasks/monitor-files` | 監控輸出清單 |
| `GET /api/tasks/monitor-data?limit=10000` | 監控 CSV 表格資料 |
| `GET /downloads/file?path=...` | path 以 report-output-dir 為根的相對路徑，可包含 SUM／分類子目錄 |

SFTP 下載／上傳仍使用 CLI 或 Jenkins，沒有 Web 對應按鈕／API。下載端點不提供 `01_excel_input/` 的原始 Excel。

查詢範例：

```bash
curl -H 'Content-Type: application/json' \
  -d '{"sql":"SELECT 1 AS ok"}' http://localhost:18080/api/query
```

查詢成功後寫入 `<base-dir>/logs/query_history.log.jsonl`；這與重啟即清空的 Web 任務歷史不同。

## 限制與執行環境

- Web 任務為非同步，送出成功不等於任務完成，需查 `/api/tasks` 的最終狀態。
- Web 內限制 Doctor → Ingest → Sync Views → Generate Report 順序，且同一時間只跑一個任務；CLI 或其他 JVM 不受這個鎖保護。
- readonly 停用任務與上傳，SQL 僅做開頭白名單檢查，不是完整解析或資料庫強制唯讀。
- 目前未內建登入與角色權限。不要把 readonly 當成可對不受信任使用者開放 SQL 的安全隔離。
- SQL 結果會一次載入記憶體，API 沒有通用分頁／列數上限；大型查詢應自行加 LIMIT。
- Web 批次清單不遞迴列出 SUM／分類檔案；需要時可用正確相對路徑下載，或直接由主機取得。
