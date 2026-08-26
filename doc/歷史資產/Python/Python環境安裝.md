# FSAP 維運月報自動化中心 - 知識庫：Python 環境安裝指南 (Windows / macOS)

> 歷史文件：僅在執行保留的 Python 舊版腳本時才需要 Python。現行 Java 版執行需求為 Java 17；請參考 [啟動與產生報表](../../人類操作/啟動與產生報表.md)。

歡迎使用 FSAP 維運月報自動化中心。本文件彙整了所有相關技術手冊與操作指南，協助您快速存取資訊。

本專案 `FSAP-Month-Report` 建議使用 **Python 3.13.x** 以獲得最佳效能與相容性（最低需求為 Python 3.10）。

---

## 🪟 Windows 安裝步驟

### 1. 下載安裝程式
- 前往 [Python 官方下載頁面](https://www.python.org/downloads/windows/)。
- 尋找 **Python 3.13.x** 版本，並下載 `Windows installer (64-bit)`。

### 2. 執行安裝程式 (關鍵步驟)
- 雙擊執行 `.exe` 安裝檔。
- **重要：** 勾選下方的 **「Add Python to PATH」**（將 Python 加入環境變數）。這是最常見的失敗原因，若未勾選將無法在終端機執行指令。
- 點擊 **「Install Now」**。

### 3. 驗證安裝
- 按下 `Win + R` 鍵，輸入 `cmd` 並確認。
- 在命令提示字元輸入以下指令：
  ```cmd
  python --version
  ```
- 若顯示 `Python 3.13.x` 即安裝成功。

### 4. Windows 專案啟動 (手動方式)
由於 Windows 預設不支援 `.sh` 腳本，請在專案根目錄開啟 `cmd` 或 `PowerShell` 執行：
```cmd
# 建立虛擬環境
python -m venv .venv

# 啟用虛擬環境
# CMD:
.venv\Scripts\activate
# PowerShell:
.\.venv\Scripts\Activate.ps1

# 安裝必要套件
pip install streamlit duckdb pandas openpyxl

# 啟動儀表板
streamlit run scripts/fsap-month-report-db.py
```

---

## 🍎 macOS 安裝步驟

### 方案 A：使用 Homebrew (推薦)
如果你有安裝 Homebrew，這是最快速的方式：
```bash
# 安裝 Homebrew (若尚未安裝)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# 安裝 Python 3.13
brew install python@3.13
```

### 方案 B：官方安裝程式
- 前往 [Python 官方下載頁面](https://www.python.org/downloads/macos/)。
- 下載 `macOS 64-bit universal2 installer`。
- 執行 `.pkg` 檔案並依照指示完成安裝。

### 2. 驗證安裝
- 開啟「終端機 (Terminal)」。
- 輸入以下指令：
  ```bash
  python3 --version
  ```
- 若顯示 `Python 3.13.x` 即安裝成功。

### 3. macOS 專案啟動 (自動方式)
在專案根目錄執行專案內建的一鍵啟動腳本：
```bash
bash scripts/start-fsap-month-report-db.sh
```

---

## 💡 常見問題 (Q&A)

### Q1: 為什麼輸入 python 顯示「找不到指令」？
- **Windows**: 通常是因為安裝時未勾選 "Add Python to PATH"。請重新執行安裝程式並選擇 "Modify"，或手動將 Python 路徑加入系統環境變數。
- **macOS**: 請嘗試使用 `python3` 而非 `python`。

### Q2: 虛擬環境 (venv) 是必須的嗎？
- **是的**。虛擬環境可以確保本專案所需的套件（如 Streamlit, DuckDB）不會與你電腦中的其他 Python 專案產生版本衝突。

### Q3: 執行腳本時出現權限不足？
- 請執行以下指令賦予腳本執行權限：
  ```bash
  chmod +x scripts/start-fsap-month-report-db.sh
  ```

---
*最後更新日期: 2026-04-20*
