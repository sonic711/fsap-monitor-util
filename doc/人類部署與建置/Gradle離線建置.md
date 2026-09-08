# Gradle 離線建置

執行既有 JAR 只需 Java 17。以下步驟適用於「離線環境仍需要從原始碼建置」；需要 **JDK 17、Gradle 8.13、原始碼與 Maven 離線倉庫**。

## 1. 線上準備

在專案根目錄執行：

```bash
GRADLE_USER_HOME=$PWD/.gradle-online ./gradlew prepareOfflineBundle
```

| 產物 | 用途 |
| --- | --- |
| `build/offline-maven-repo.zip` | 套件及 buildscript 插件依賴，已整理成 Maven 目錄 |
| `build/offline-gradle/gradle-8.13-all.zip` | Gradle 執行工具本身 |
| `build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar` | 本專案的可執行 JAR |

只要 Maven 倉庫可執行 `zipOfflineMavenRepo`；只缺 Gradle ZIP 可執行 `downloadGradleDistribution`。

預設 `resolved` 模式只挑目前專案及 buildscript 解析到的 JAR/AAR，但保留 cache 中的 POM/module 描述檔，並補齊可解析的 parent POM 與 BOM。它不是整份 `.gradle` 備份，也不保證倉庫沒有 CVE。

要把 task 引入其他專案，或轉換整份 cache，請看 [離線Maven倉庫Task](Gradle離線Maven倉庫Task.md)。

## 2. 搬到離線主機

搬移原始碼（含 `gradlew`、`gradlew.bat`、`gradle/wrapper/`）、兩份 ZIP，並準備 JDK 17。

以下假設 ZIP 放在 `/opt/fsap/offline/`。將 Maven 倉庫解壓到專案 `build/` 以外，避免 `clean` 把它刪除：

```bash
unzip /opt/fsap/offline/offline-maven-repo.zip -d /opt/fsap/offline/repo
```

Gradle ZIP 有兩種用法，擇一即可：

| 用法 | 設定 |
| --- | --- |
| 使用專案 Wrapper | 將 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改為 `file:///opt/fsap/offline/gradle-8.13-all.zip`，ZIP 不必手動解壓 |
| 直接使用 Gradle | 將 ZIP 解壓到工具目錄，使用其中 `gradle-8.13/bin/gradle` |

`--offline` 不會替 Wrapper 準備 Gradle 本身；必須先完成上述其中一種設定。

## 3. 離線打包

在專案根目錄執行：

```bash
./gradlew --offline -PofflineRepo=/opt/fsap/offline/repo clean bootJar
```

Windows 使用 `gradlew.bat`，並將倉庫路徑改成 Windows 絕對路徑。完成後 JAR 位於 `build/libs/`。

本專案已在一般依賴與 buildscript 宣告 `mavenLocal()`。若不想每次指定 `-PofflineRepo`，可將 ZIP 內容解到 `~/.m2/repository` 後執行：

```bash
./gradlew --offline clean bootJar
```

兩種方式擇一。調整其他專案時，需同時設定它自己的插件與一般套件倉庫，不能只複製這條指令。

## 4. 驗證離線包是否完整

在原始碼副本測試，使用空白 Gradle cache，並避免共用 Maven local 掩蓋缺漏。下列範例使用本機解壓的 Gradle，避免 Wrapper 再連網下載。先將 ZIP 放在上述 `/opt/fsap/offline/`。

```bash
checkDir=$(mktemp -d "$PWD/offline-check.XXXXXX")
unzip -q /opt/fsap/offline/offline-maven-repo.zip -d "$checkDir/repo"
unzip -q /opt/fsap/offline/gradle-8.13-all.zip -d "$checkDir/tool"
GRADLE_USER_HOME="$checkDir/gradle-home" \
  "$checkDir/tool/gradle-8.13/bin/gradle" \
  --offline -Dmaven.repo.local="$checkDir/empty-maven-local" \
  -PofflineRepo="$checkDir/repo" bootJar --rerun-tasks
```

成功表示離線包涵蓋此次 `bootJar` 所需依賴；若還要離線執行測試或其他 task，需另驗證那些 task。此檢查目錄須位於允許執行工具的掛載點。

## 常見問題

| 現象 | 處理 |
| --- | --- |
| 找不到 `gradle-8.13-all.zip` | 執行 `downloadGradleDistribution`，或檢查 bundle task 是否失敗 |
| 插件或 parent POM 找不到 | 檢查 buildscript 倉庫與 metadata；重新在線上產包並用乾淨 cache 驗證 |
| 執行 clean 後倉庫消失 | 將 repo 搬到 `build/` 外 |
| 改版後離線失敗 | 新增或升級依賴、插件後，重新產生離線包 |

僅線上建置 JAR 可執行 `./gradlew bootJar`。服務操作見 [啟動與產生報表](../人類操作/啟動與產生報表.md)。
