# 腳本目錄說明 (scripts)

> 歷史文件：`fsap-month-report-develop/scripts/` 下的 Python 腳本保留作為舊版比對用途。現行日常操作使用根目錄 Java JAR 的 CLI 或 Web UI，請參考 [啟動與產生報表](../../人類操作/啟動與產生報表.md)。

本目錄存放專案的核心執行腳本，涵蓋了從數據匯入、邏輯處理、報表產出到線上查詢服務的所有自動化邏輯。這些腳本是「自動化工廠」運作的動力來源。

---

## 1. 數據加工流程腳本
| 腳本名稱 | 功能說明 | 關鍵任務 |
| :--- | :--- | :--- |
| `step1_ingest.py` | 數據標準化工廠 | 將 Excel 原始資料轉化為標準 JSONL 格式 (Ingestion)。 |
| `update_views_to_db.py` | View 同步工具 | 將 `03_sql_logic/views` 中的 SQL 固化至 DuckDB。 |
| `step2_report.py` | 報表產出引擎 | 執行 `03_sql_logic/reports` SQL，產出最終 Excel/CSV 報表。 |

## 2. 線上服務與儀表板腳本
| 腳本名稱 | 功能說明 | 關鍵任務 |
| :--- | :--- | :--- |
| `api_server.py` | REST API 服務 | 提供 FastAPI 介面，支援 SQL 查詢請求，供下游應用存取。 |
| `fsap-month-report-db.py` | 互動查詢儀表板 | 基於 Streamlit 的自助式數據探索介面。 |

## 3. 自動化維運輔助腳本
| 腳本名稱 | 功能說明 | 關鍵任務 |
| :--- | :--- | :--- |
| `start-fsap-month-report-db.sh` | 環境一鍵啟動 | 自動化處理虛擬環境建立、依賴安裝與儀表板啟動。 |
| `update_monitor_data.py` | 監控數據同步 | 透過 API 呼叫定期獲取監控數據並產出 CSV/JS 檔案。 |

---

## 4. 運作注意事項

### 執行環境
- **虛擬環境**：執行所有 Python 腳本前，建議確保已啟用專案目錄下的 `.venv`。
- **工作目錄 (CWD)**：大多數腳本內建路徑處理邏輯，預設皆以「專案根目錄」作為相對路徑的基準點，請確保在根目錄下執行，避免路徑解析錯誤。

### 參數與設定
- 若需調整自動化行為（如 ingest 的檔案數量、監控數據的同步任務），請參閱對應目錄下的 `config.json` 或直接使用腳本的命令行參數 (CLI Arguments)。

---
*最後更新日期: 2026-04-20*
