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

`resolved` 是預設模式，掃描目前專案可解析的 configurations，預設也包含 buildscript。JAR／AAR 依解析到的 group、artifact、version 篩選，而非只依 runtimeClasspath 的實際檔案；同座標其他已快取 classifier 可能一起收錄。所有已快取 POM／module metadata 都保留，並非完全精簡的最小倉庫。

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

注意：all-cache 可能包含其他專案與舊版套件。resolved 可縮小二進位套件範圍，但仍保留其他版本 metadata，不代表已清除所有弱掃命中。

空專案至少需要 settings.gradle、build.gradle、這份 script 與可用的 Gradle。最小 settings.gradle：

```groovy
rootProject.name = 'offline-cache-export'
```

最小 build.gradle：

```groovy
apply from: "${rootDir}/gradle/offline-maven-repo.gradle"
offlineMavenRepo {
    artifactSelectionMode = 'all-cache'
    prepareTaskNames = []
}
```

若未準備 Wrapper，可使用已解壓 Gradle 的 bin/gradle 執行；`--offline` 不能替 Wrapper 下載 Gradle 本體。

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
| `prepareTaskNames` | 未指定時採 `bootJar`, `jar`, `testClasses` 中存在者 | 本專案覆寫為 bootJar、testClasses；空清單表示不跑前置 task，未找到的名稱會略過 |

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
GRADLE_USER_HOME=/path/to/copied/.gradle ./gradlew --offline zipOfflineMavenRepo
```

## 6. 離線環境使用方式

### 6.1 使用 -PofflineRepo

先解壓：

```bash
unzip offline-maven-repo.zip -d /opt/fsap/offline-maven-repo
```

離線建置：

```bash
./gradlew --offline -PofflineRepo=/opt/fsap/offline-maven-repo bootJar
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
./gradlew --offline bootJar
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
- 每次準備會刪除並重建 outputDir，不能直接指向正式 Maven local 或其他重要資料目錄；先輸出到專用目錄，再搬移或解壓。
- 此 script 只處理套用它的 project 配置，不會自動巡覽整個多模組專案。其他專案可能還需 Node、原生工具或非 Maven cache，須另行準備。
- 插件與 parent POM／BOM 只在已快取且可辨識的範圍內提供；Maven local 有而 Gradle module cache 沒有的檔案可能遺漏。
- 引入此 script 只新增兩個倉庫 task，不會新增本專案的 `downloadGradleDistribution` 或 `prepareOfflineBundle`。
- `-PofflineRepo` 需目標專案自行讀取與設定。使用 `plugins {}` 的專案，還需在 `settings.gradle` 的 `pluginManagement.repositories` 指向離線倉庫，並備妥 plugin marker 與插件依賴。
- 產生 ZIP 成功只代表轉換完成，仍需實際離線建置驗證。
