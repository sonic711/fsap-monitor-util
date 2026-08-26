# 🚀 Step 1: 數據標準化工廠 (step1_ingest.py)

> 歷史文件：本檔描述保留的 Python `step1_ingest.py`。現行匯入命令為 Java `ingest`，請參考 [啟動與產生報表](../../人類操作/啟動與產生報表.md)。

## 1. 程式定位
`step1_ingest.py` 是自動化報表系統的第一步。它的任務是將「原料倉」中雜亂、非結構化的 Excel 原始資料，轉化為「數據湖」中統一、標準且具備追溯性的壓縮 JSONL 格式。

---

## 2. 運行環境與依賴套件

### 🐍 Python 版本需求
- **建議版本**：Python 3.10 以上 (支援現代的類型檢查與路徑處理)。

### 📦 必要套件安裝
本程式依賴 `pandas` 處理數據以及 `openpyxl` 讀取現代 Excel 檔案 (.xlsx)。

請在您的虛擬環境執行以下指令：
```bash
pip install pandas openpyxl
```

> **注意**：如果您使用的是專案內建的啟動腳本 `./scripts/start-fsap-month-report-db.sh`，系統會自動幫您安裝這些套件。

---

## 3. 核心功能說明

### 💡 增量匯入模式 (Incremental Mode) - 預設
為了提升效率，程式內建了「智慧跳過」邏輯：
- 執行時會自動對比 `01_excel_input` (原始檔) 與 `02_source_lake` (成品)。
- 若該日期的資料已經轉換過且存在於數據湖，則會顯示 `⏭️ [跳過]`，不重複開啟 Excel，節省大量運算時間。

### 🔥 強制重轉模式 (Force Mode)
若原始 Excel 資料有修正，需要重新覆蓋數據湖：
- 使用 `--force` 參數，程式會忽略現有成品，強制重新讀取所有 Excel 並覆蓋 `02_source_lake` 中的資料。

### 🛠 數據清洗與增強
在轉換過程中，程式會自動執行以下動作：
1. **標準化日期**：將 Excel 內各種格式的日期統一轉為 ISO 格式。
2. **處理空值**：自動將 Excel 的無效單元格轉為標準的 JSON Null。
3. **注入追蹤標記 (Metadata)**：為每筆資料補上來源標籤：
    - `_file`: 來源原始檔名。
    - `_sheet`: 所屬分頁名稱。
    - `_dt`: 從檔名辨識出的業務日期。
    - `_ingest_ts`: 處理當下的時間戳記。

---

## 4. 操作範例

### 補齊缺失的資料 (常用)
```bash
python3 scripts/step1_ingest.py
```

### 全量重新加工
```bash
python3 scripts/step1_ingest.py --force
```

---

## 5. 處理的目標分頁 (Target Sheets)
程式會從 Excel 中精確擷取以下對維運報表有價值的數據：
- `RT_CNT`, `RT_TMSPT`, `RT_PR_HH24`, `RT_NODE_HH24` (即時交易)
- `BT_CNT` (批次作業)
- `MON_LOG`, `ERR_LOG` (監控與錯誤)


## 6. 資料處理範例 (Data Samples)

轉換後的數據存於 `02_source_lake/` 目錄，採用壓縮的 `.jsonl` 格式，每行皆為獨立的 JSON 物件：

### 📁 RT_PR_HH24 (小時級交易)
```json
{"PR_ID": "C512", "CALTM": "0419 00", "AVG_TM": 0.311, "CNT": 2, "_file": "FSAP每日交易統計20260420.xlsx", "_sheet": "RT_PR_HH24", "_dt": "20260420", "_ingest_ts": 1776670729}
{"PR_ID": "FAC2FAS", "CALTM": "0419 23", "AVG_TM": 0.109, "CNT": 2212, "_file": "FSAP每日交易統計20260420.xlsx", "_sheet": "RT_PR_HH24", "_dt": "20260420", "_ingest_ts": 1776670729}
```

### 📁 MON_LOG (系統監控日誌)
```json
{"MONITOR_KIND": "JVM", "TARGET_IP": "10.4.240.183", "MON_CONTENT": "JVM資源使用狀況:usedHeapMem:3363.00,usedMemRate:20.53%", "CREATETIME": "2026-04-19T03:29:24", "_dt": "20260420"}
{"MONITOR_KIND": "SERVER", "TARGET_IP": "10.4.240.183", "MON_CONTENT": "虛擬伺服器資源使用狀況:usedCPURate:2.04%,usedMemRate:48.55%", "CREATETIME": "2026-04-19T22:43:52", "_dt": "20260420"}
```

### 📁 RT_CNT (日級交易總計)
```json
{"CALDY": "0419", "PR_ID": "C512", "總筆數": 2, "成功": 2, "失敗": 0, "_file": "FSAP每日交易統計20260420.xlsx", "_dt": "20260420"}
{"CALDY": "0419", "PR_ID": "FAC2FAS", "總筆數": 36242, "成功": 36242, "失敗": 0, "_file": "FSAP每日交易統計20260420.xlsx", "_dt": "20260420"}
```

---
*文件更新於: 2026-04-20*
