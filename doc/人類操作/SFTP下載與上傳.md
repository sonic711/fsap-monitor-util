# SFTP 下載與上傳

先依 [啟動與產生報表](啟動與產生報表.md) 設定 Shell 的 `fsap` 指令簡寫。本頁範例沿用該設定。

## 連線設定

JAR 旁的 `config/application.yml` 使用既有連線設定：

```yaml
remote:
  url: sftp://your-sftp-host:22
  username: fsap
  code: <既有加密密碼>
  defPath: FSAP/
```

替換主機與帳號；`code` 沿用部署環境提供的加密值。

## 下載每日 Excel

```bash
fsap download-input --date 20260801
```

未傳 `--date` 時下載今天的檔案。程式在 `/FSAP/FILE_BCKP` 的子目錄搜尋 `FSAP每日交易統計YYYYMMDD.xlsx`，下載到 `<base-dir>/01_excel_input/`；不需要自行推算遠端資料夾日期。

| 情況 | 指令 |
| --- | --- |
| 本機已有同名檔，需要替換 | `fsap download-input --date 20260801 --overwrite` |
| 指定搜尋根目錄與檔名 | `fsap download-input --remote-root /FSAP/FILE_BCKP --filename FSAP每日交易統計20260801.xlsx` |
| 下載完成後匯入 | `fsap ingest --date 20260801` |

預設不覆蓋同名檔。若更正的日期已經匯入，後續 ingest 需加 `--force`，否則既有 JSONL.GZ 會被跳過。

## 上傳彙總 Excel

```bash
fsap upload-report
```

預設選取最新報表批次中的 Excel，上傳到最近一次成功下載的來源目錄。目的地記錄在 `<base-dir>/logs/latest-sftp-download.json` 的 `remoteDirectory`，不會按報表月份自動重新選擇。

要補發特定報表時，明確指定完整本機路徑與目的地：

```bash
fsap upload-report \
  --local-file /app/fsap-monitor-util/fsap-month-report-develop/04_report_output/202609020930/維運月度報表_08月彙總_202609020930.xlsx \
  --remote-dir /FSAP/FILE_BCKP/01150901
```

請替換成已確認的報表與遠端目錄。`--local-file` 相對路徑以工作目錄解析，不是以 `base-dir` 解析。遠端已有同名檔時會停止，確認要替換才加 `--overwrite`。

手動放入 Excel 不會產生下載記錄；沒有 `latest-sftp-download.json` 時，上傳必須指定 `--remote-dir`。需要使用另一份下載記錄可傳 `--metadata-file`。

## 查錯與排程

| 檔案（位於 `<base-dir>/logs/`） | 用途 |
| --- | --- |
| `sftp_download.log` | 搜尋與下載結果 |
| `latest-sftp-download.json` | 最近成功下載的來源與上傳預設目的地 |
| `sftp_upload.log` | 上傳檔案、目的地與失敗原因 |

每日產報與每月上傳分成兩個 Job，流程及補檔限制見 [Jenkins 補檔與排程](啟動與產生報表.md#5-jenkins-補檔與排程)。
