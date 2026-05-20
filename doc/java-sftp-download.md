# Java SFTP Download

本文件說明 Java 版 SFTP 下載入口，用來把每日交易統計 Excel 從 SFTP 備份目錄下載到 `01_excel_input`，再交給既有 `ingest` 流程處理。

## 目標

每日來源檔案位於 SFTP：

```text
/FSAP/FILE_BCKP/0{民國年月日}/FSAP每日交易統計{今天西元年月日}.xlsx
```

其中 `0{民國年月日}` 是前營業日資料夾，執行者不需要知道實際是哪一天。程式會在 `/FSAP/FILE_BCKP` 底下搜尋包含目標檔名的子目錄。

## 設定

SFTP 連線設定放在 `application.yml`：

```yaml
remote:
  url: sftp://10.1.11.47:22
  username: ncb
  code: <encrypted-password>
  defPath: NCB/
```

本機下載目錄由 `fsap.paths.base-dir` 與 `fsap.paths.input-dir` 決定。正式部署建議使用：

```bash
--fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop
```

則下載目標會是：

```text
/app/fsap-monitor-util/fsap-month-report-develop/01_excel_input
```

## CLI

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

## 後續流程

下載完成後可接續執行：

```bash
java -jar fsap-monitor-util-0.1.0-SNAPSHOT.jar \
  --fsap.paths.base-dir=/app/fsap-monitor-util/fsap-month-report-develop \
  ingest --date 20260520
```
