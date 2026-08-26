# Java SFTP Download / Upload

本文件說明 Java 版 SFTP 下載與上傳入口：`download-input` 會把每日交易統計 Excel 從 SFTP 備份目錄下載到 `01_excel_input`，`upload-report` 會把最新產出的月報 Excel 上傳回最近一次下載來源所在的 SFTP 目錄。

## 目標

每日來源檔案位於 SFTP：

```text
/FSAP/FILE_BCKP/0{民國年月日}/FSAP每日交易統計{今天西元年月日}.xlsx
```

其中 `0{民國年月日}` 是前營業日資料夾，執行者不需要知道實際是哪一天。程式會在 `/FSAP/FILE_BCKP` 底下搜尋包含目標檔名的子目錄。

## 設定

SFTP 連線設定放在 JAR 外部的 `config/application.yml`。程式啟動時會自動讀取 JAR 所在目錄與目前工作目錄下的 `config/application.yml`，Web `serve` 與所有 CLI command 都適用：

```yaml
remote:
  url: sftp://10.1.11.47:22
  username: fsap
  code: <encrypted-password>
  defPath: FSAP/
```

正式部署建議目錄結構：

```text
fsap-monitor-util-0.1.0-SNAPSHOT.jar
config/application.yml
config/monitor-data.json
```

本機下載目錄由 `fsap.paths.base-dir` 與 `fsap.paths.input-dir` 決定。正式部署建議使用：

```bash
--fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop
```

則下載目標會是：

```text
/app/fsap-monitor-util/fsap-month-report-develop/01_excel_input
```

## 下載 CLI

預設下載今天的檔案：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop \
  download-input
```

指定日期：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop \
  download-input --date 20260520
```

若本機已存在同名檔案，預設會停止並報錯。需要覆蓋時加上：

```bash
download-input --date 20260520 --overwrite
```

可覆寫遠端根目錄與檔名：

```bash
download-input \
  --remote-root /FSAP/FILE_BCKP \
  --filename FSAP每日交易統計20260520.xlsx
```

## 搜尋規則

1. 組出目標檔名：`FSAP每日交易統計{yyyyMMdd}.xlsx`
2. 進入遠端根目錄，預設 `/FSAP/FILE_BCKP`
3. 掃描子目錄，優先檢查符合 `0ddddddd` 的民國日期資料夾，並由新到舊排序
4. 找到第一個包含目標檔名的目錄後下載
5. 檔案寫入 `ProjectPathService.inputDir()`

## Log

SFTP 下載會同時輸出 console log 與執行紀錄檔：

```text
{fsap.paths.base-dir}/logs/sftp_download.log
```

正式部署若使用：

```bash
--fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop
```

則 log 會寫在：

```text
/app/fsap-monitor-util/fsap-month-report-develop/logs/sftp_download.log
```

log 會記錄下載開始、連線目標、掃描目錄數、命中的遠端檔案、下載成功位置與失敗原因。紀錄中只會寫入 `remote.url` 與 `remote.username`，不會寫入密碼或解密後的 `remote.code`。

下載成功後也會寫入最新下載來源 metadata：

```text
{fsap.paths.base-dir}/logs/latest-sftp-download.json
```

這個檔案會保存檔名、遠端完整路徑、遠端目錄、本機路徑與下載時間，供 `upload-report` 預設判斷要上傳回哪個 SFTP 目錄。

## 上傳 CLI

預設上傳最新報表批次中的 `.xlsx`，並使用 `logs/latest-sftp-download.json` 內的 `remoteDirectory` 作為遠端目錄：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop \
  upload-report
```

可手動指定本機報表檔與遠端目錄：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop \
  upload-report \
  --local-file 04_report_output/202606010930/維運月度報表_05月彙總_202606010930.xlsx \
  --remote-dir /FSAP/FILE_BCKP/01150526
```

若遠端已有同名檔案，預設會停止並報錯。需要覆蓋時加上：

```bash
upload-report --overwrite
```

若要指定其他下載 metadata 檔：

```bash
upload-report --metadata-file logs/latest-sftp-download.json
```

上傳紀錄會寫入：

```text
{fsap.paths.base-dir}/logs/sftp_upload.log
```

## 後續流程

下載完成後可接續執行：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop \
  ingest --date 20260520
```

完整 SFTP 主線通常可拆成兩個 Jenkins Job：

每日資料處理 Job 使用 `Jenkinsfile`：

1. `download-input`
2. `ingest`
3. `sync-views`
4. `generate-report`

月報上傳 Job 使用 `Jenkinsfile.upload`：

1. `doctor`
2. `generate-report`
3. `upload-report`

這樣每天只會產生本機彙總報告，不會每天上傳到 SFTP；上傳時機由 Jenkins 月排程或人工 Build with Parameters 控制。
