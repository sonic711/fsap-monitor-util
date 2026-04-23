# Java Gradle 建置與離線 Maven Repository 準備流程

更新日期：2026-04-23

## 1. 目前建置基準

- Java：17
- Spring Boot：3.5.6
- Gradle Wrapper：8.13 all distribution
- 主要建置檔：`build.gradle`
- Wrapper 設定：`gradle/wrapper/gradle-wrapper.properties`
- 輸出 JAR：`build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar`

本專案已改為 Gradle 專案，不再使用 `pom.xml` 打包。

## 2. 線上環境打包

在有網路的環境執行：

```bash
./gradlew clean bootJar -x test
```

完成後可用下列方式啟動：

```bash
java -jar build/libs/fsap-monitor-util-0.1.0-SNAPSHOT.jar --help
```

## 3. 是否可以用 Gradle 產出 Maven 離線倉庫

可以。

但重點是：不能把 Gradle cache 原封不動當 Maven repository。離線環境若要用 Maven repository 形式解析依賴，必須準備 Maven repository layout：

```text
group/id/artifact/version/artifact-version.jar
group/id/artifact/version/artifact-version.pom
```

本專案提供 `prepareOfflineMavenRepo` 與 `zipOfflineMavenRepo`，會把 Gradle 已解析到的 module cache 轉成 Maven layout。

### 3.1 官方依據

- Gradle 官方文件說明可宣告本機 Maven repository，例如 `maven { url = uri("/path/to/local/repo") }`。
- Maven 官方文件定義 Maven repository layout：`groupId` 需把 `.` 轉成 `/`，再接 `artifactId/version/artifact-version.ext`。
- Maven 官方文件也說明，不符合 layout 的檔案無法用 Maven coordinates 定位。
- Gradle 官方文件另有說明 dependency cache 可以複製與重用，但那是 Gradle cache 用途，不等同於 Maven repository。

因此本專案採用的做法是：

1. 先讓 Gradle 在線上環境解析依賴並寫入 Gradle module cache。
2. 從 Gradle cache 抽出 `.jar`、`.pom`、`.module`。
3. 重新排成 Maven repository layout。
4. 離線建置時用 `maven { url = uri(offlineRepoPath) }` 指向該目錄。

參考：

- `https://docs.gradle.org/current/userguide/declaring_repositories_basics.html`
- `https://docs.gradle.org/current/userguide/supported_repository_types.html`
- `https://docs.gradle.org/current/userguide/dependency_caching.html`
- `https://maven.apache.org/repositories/layout.html`

## 4. 建議的線上準備方式

建議使用專案內獨立的 Gradle cache，避免把本機其他專案的依賴一起包進去：

```bash
GRADLE_USER_HOME=$PWD/.gradle-online ./gradlew clean bootJar testClasses zipOfflineMavenRepo
```

產物位置：

```text
build/offline-maven-repo/
build/offline-maven-repo.zip
```

如果離線環境無法下載 Gradle wrapper distribution，線上環境也要先下載：

```bash
GRADLE_USER_HOME=$PWD/.gradle-online ./gradlew downloadGradleDistribution
```

產物位置：

```text
build/offline-gradle/gradle-8.13-all.zip
```

也可以一次準備 Maven repo zip 與 Gradle distribution：

```bash
GRADLE_USER_HOME=$PWD/.gradle-online ./gradlew prepareOfflineBundle
```

## 5. 搬到離線環境需要的檔案

若離線環境沒有預先安裝 Gradle 8.13，最少需要：

- 專案原始碼
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`
- `build/offline-maven-repo.zip`
- `build/offline-gradle/gradle-8.13-all.zip`
- Java 17 Runtime 或 JDK

如果離線環境已經預先安裝乾淨可用的 Gradle 8.13，則 `gradle-8.13-all.zip` 可省略，也可以直接用系統 `gradle` 指令打包。

## 6. 離線環境建置

先解壓 Maven repository：

```bash
unzip offline-maven-repo.zip -d offline-maven-repo
```

若 wrapper 不能連網下載 Gradle，請將 `gradle-8.13-all.zip` 放在離線機器可讀位置，並把 `gradle/wrapper/gradle-wrapper.properties` 的 `distributionUrl` 改為 file URL，例如：

```properties
distributionUrl=file:///opt/fsap/offline-gradle/gradle-8.13-all.zip
```

再使用離線 Maven repo 建置：

```bash
./gradlew --offline -PofflineRepo=/opt/fsap/offline-maven-repo clean bootJar -x test
```

若離線環境已經有 Gradle 8.13，也可不用 wrapper：

```bash
gradle --offline -PofflineRepo=/opt/fsap/offline-maven-repo clean bootJar -x test
```

Windows 範例：

```bat
gradlew.bat --offline -PofflineRepo=C:\fsap\offline-maven-repo clean bootJar -x test
```

## 7. 驗證離線包是否完整

在線上環境產出 `offline-maven-repo.zip` 後，建議立刻用乾淨 Gradle cache 驗證一次：

```bash
mkdir -p /tmp/fsap-gradle-offline-check
rm -rf /tmp/fsap-offline-maven-repo
mkdir -p /tmp/fsap-offline-maven-repo
unzip -q build/offline-maven-repo.zip -d /tmp/fsap-offline-maven-repo
GRADLE_USER_HOME=/tmp/fsap-gradle-offline-check ./gradlew --offline -PofflineRepo=/tmp/fsap-offline-maven-repo bootJar -x test --rerun-tasks
```

如果這一步成功，代表離線 Maven repository 至少已涵蓋目前打包所需依賴。

注意：不要在驗證時直接把 `-PofflineRepo` 指到 `build/offline-maven-repo` 再執行 `clean`，因為 `clean` 會刪除 `build/`，連同剛產生的離線 repo 一起移除。

## 8. 注意事項

- 不要使用動態版本，例如 `latest.release` 或 `1.+`，否則離線解析會不穩定。
- 若新增 dependency、升級 Spring Boot 或 Gradle plugin，需要重新產出 `offline-maven-repo.zip`。
- Gradle wrapper distribution 與 Maven dependency repository 是兩件事；離線環境兩者都要能取得。
- `prepareOfflineMavenRepo` 會從目前 `GRADLE_USER_HOME` 複製 Gradle module cache，因此建議搭配專用 `.gradle-online` 目錄使用。
