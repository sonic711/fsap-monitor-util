# 引入離線 Maven 倉庫 Task

本文件說明如何把本專案的離線 Maven repository 轉換 task 引入其他 Gradle 專案，並產出可供離線建置使用的 Maven layout 壓縮檔。

## 1. 複製 task script

將下列檔案複製到目標 Gradle 專案：

```text
gradle/offline-maven-repo.gradle
```

建議放在目標專案同樣的位置：

```text
your-project/
  build.gradle
  settings.gradle
  gradle/
    offline-maven-repo.gradle
```

## 2. 在 build.gradle 引入

在目標專案的 `build.gradle` 加入：

```groovy
apply from: "${rootDir}/gradle/offline-maven-repo.gradle"
```

引入後會新增兩個 task：

```text
prepareOfflineMavenRepo
zipOfflineMavenRepo
```

可用下列指令確認：

```bash
./gradlew tasks --group offline
```

## 3. 模式選擇

### 3.1 resolved 模式

`resolved` 是預設模式，只打包目前專案實際解析到的 `.jar` / `.aar`，並保留 `.pom` / `.module` metadata。

適合依正式專案目前解析的版本產生離線包：

```groovy
offlineMavenRepo {
    artifactSelectionMode = 'resolved'
    prepareTaskNames = ['bootJar', 'testClasses']
}
```

一般 Java 專案可改成：

```groovy
offlineMavenRepo {
    artifactSelectionMode = 'resolved'
    prepareTaskNames = ['jar', 'testClasses']
}
```

### 3.2 all-cache 模式

`all-cache` 會把目前 `GRADLE_USER_HOME/caches/modules-2/files-2.1` 裡的 `.jar`、`.aar`、`.pom`、`.module` 全部轉成 Maven layout。

適合空專案只拿來把整包 `.gradle` cache 轉成 Maven 離線倉庫：

```groovy
offlineMavenRepo {
    artifactSelectionMode = 'all-cache'
    prepareTaskNames = []
}
```

注意：`all-cache` 會把 cache 裡所有 jar/aar 都打包，可能包含其他專案或舊版弱點 jar。若目標是降低弱掃命中，請使用 `resolved`。

## 4. 可調整設定

```groovy
offlineMavenRepo {
    outputDir = layout.buildDirectory.dir('offline-maven-repo')
    zipFileName = 'offline-maven-repo.zip'
    includeBuildscript = true
    artifactSelectionMode = 'resolved'
    prepareTaskNames = ['bootJar', 'testClasses']
}
```

設定說明：

| 設定 | 預設值 | 說明 |
| --- | --- | --- |
| `outputDir` | `build/offline-maven-repo` | Maven layout repo 輸出目錄 |
| `zipFileName` | `offline-maven-repo.zip` | 壓縮檔名稱 |
| `includeBuildscript` | `true` | 是否納入 `buildscript` classpath 依賴，例如 Gradle plugin |
| `artifactSelectionMode` | `resolved` | `resolved` 或 `all-cache` |
| `prepareTaskNames` | `bootJar`, `jar`, `testClasses` 中存在者 | 產生離線 repo 前要先執行的 task |

## 5. 產生 Maven 離線包

建議在線上環境使用獨立 Gradle cache，避免混到本機其他用途的 cache：

```bash
GRADLE_USER_HOME=$PWD/.gradle-online ./gradlew zipOfflineMavenRepo
```

產物位置：

```text
build/offline-maven-repo/
build/offline-maven-repo.zip
```

若是空專案轉換一整包既有 `.gradle` cache：

```bash
GRADLE_USER_HOME=/path/to/copied/.gradle ./gradlew zipOfflineMavenRepo
```

## 6. 離線環境使用方式

### 6.1 使用 -PofflineRepo

先解壓：

```bash
unzip offline-maven-repo.zip -d /opt/fsap/offline-maven-repo
```

離線建置：

```bash
./gradlew --offline -PofflineRepo=/opt/fsap/offline-maven-repo bootJar -x test
```

目標專案的 `build.gradle` 需要有類似設定：

```groovy
def offlineRepoPath = gradle.startParameter.projectProperties['offlineRepo'] ?: System.getProperty('offlineRepo')

repositories {
    mavenLocal()
    if (offlineRepoPath) {
        maven { url = uri(offlineRepoPath) }
    }
    mavenCentral()
}
```

若有使用 `buildscript` repositories，也要同樣加入 `offlineRepoPath`。

### 6.2 使用 Maven local

若不想加 `-PofflineRepo`，可直接解壓到 Maven local：

```bash
unzip offline-maven-repo.zip -d ~/.m2/repository
./gradlew --offline bootJar -x test
```

前提是目標專案 repositories 已宣告：

```groovy
repositories {
    mavenLocal()
    mavenCentral()
}
```

## 7. 驗證與限制

使用 [Gradle離線建置](Gradle離線建置.md#4-驗證離線包是否完整) 的乾淨 cache 流程驗證，並將範例的 `bootJar` 換成目標專案實際需要的 task。

- `prepareTaskNames = []` 代表不先執行任何建置 task，適合空專案轉 cache。
- `all-cache` 只轉換指定 cache 已有的 JAR、AAR、POM、module，不會補出未曾下載的檔案，也不包含 Gradle distribution 或其他工具 cache。
- 引入此 script 只新增兩個倉庫 task，不會新增本專案的 `downloadGradleDistribution` 或 `prepareOfflineBundle`。
- `-PofflineRepo` 需目標專案自行讀取與設定。使用 `plugins {}` 的專案，還需在 `settings.gradle` 的 `pluginManagement.repositories` 指向離線倉庫，並備妥 plugin marker 與插件依賴。
- 產生 ZIP 成功只代表轉換完成，仍需實際離線建置驗證。
