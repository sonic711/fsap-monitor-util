# FSAP 維運月報自動化中心 - 知識庫：API Server 文件

歡迎使用 FSAP 維運月報自動化中心。本文件彙整了所有相關技術手冊與操作指南，協助您快速存取資訊。

> 歷史文件：本檔描述保留的 FastAPI 版 `scripts/api_server.py`。現行 API 由 Java Spring Boot 提供，請參考 [啟動與產生報表](../../人類操作/啟動與產生報表.md)；現行端點為 `/health`、`/api/query`、`/api/schema` 與 `/api/tasks/*`。

本專案提供一個輕量級的 REST API 接口，允許前端應用程式（如 HTML/JS 儀表板）直接向 DuckDB 執行 SQL 查詢並獲取結果。

## 1. 核心架構
- **框架**: FastAPI
- **資料庫引擎**: DuckDB
- **資料處理**: Pandas
- **連線策略**: 即時連線 (每次請求建立連線，結束即關閉)，確保並發讀取的穩定性。

## 2. 啟動方式
建議在專案根目錄執行，以確保路徑正確解析：

```bash
# 啟動命令
uvicorn scripts.api_server:app --port 8000 --reload
```

## 3. API 端點說明

### POST `/api/query`
執行 SQL 查詢。

* **Request Body (JSON)**:
  ```json
  {
    "sql": "SELECT * FROM main.v_rt_pr_hh24_clean LIMIT 5"
  }
  ```
* **Response (JSON)**:
  ```json
  {
    "status": "success",
    "data": [ ... ],
    "count": 5
  }
  ```

### GET `/health`
檢查服務狀態。

* **Response**:
  ```json
  {"status": "online"}
  ```

## 4. 注意事項
- **安全性**: 目前 CORS 設定為 `allow_origins=["*"]`，僅建議在內部網路環境使用。
- **路徑解析**: 本程式使用動態路徑定位，會自動尋找專案根目錄下的 `05_database/fsap-month-report.duckdb`。
- **環境依賴**: 請確保已安裝必要套件：
  ```bash
  pip install fastapi uvicorn pandas duckdb
  ```
