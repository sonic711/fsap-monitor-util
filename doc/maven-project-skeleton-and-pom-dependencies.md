# FSAP Maven 專案骨架與 pom 依賴清單

> 狀態：歷史文件。v1.1 之後專案已改為 Gradle Wrapper 建置，Spring Boot 版本改為 `3.5.6`，`pom.xml` 已移除。請不要再依本文件建立或維護 Maven build；實際建置與離線 repository 準備請以 `doc/java-gradle-offline-build.md` 為準。

本文件承接以下既有決策：

- 採用 `Java CLI + Spring Boot + Thymeleaf/HTMX + DuckDB JDBC`
- 建置工具採用 `Maven`
- 保留 `DuckDB`
- 需支援離線部署

本文件目的，是把 Java 專案的第一版骨架與 `pom.xml` 依賴定義清楚，讓後續可以直接開始建專案，而不是再回頭討論 build 結構。

---

## 1. 第一版骨架原則

第一版建議採用：

1. **單一 Maven 模組**
2. **單一 Spring Boot application**
3. **CLI 與 Web 同專案**
4. **既有資料目錄不先搬動**

理由：

- 目前還在移植期，不適合先做多模組工程複雜化
- CLI 與 Web 會共用大量 service / DuckDB / 路徑 / 輸出邏輯
- 單模組最容易打出一個可離線交付的 `fat jar`

---

## 2. 建議專案目錄

```text
fsap-monitor-util/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/fsap/monitor/
│   │   │   ├── FsapApplication.java
│   │   │   ├── cli/
│   │   │   ├── core/
│   │   │   ├── infra/
│   │   │   ├── web/
│   │   │   └── shared/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── templates/
│   │       ├── static/
│   │       └── logback-spring.xml
│   └── test/
│       └── java/com/fsap/monitor/
├── config/
│   └── monitor-data.json
├── 00_info/
├── 01_excel_input/
├── 02_source_lake/
├── 03_sql_logic/
├── 04_report_output/
├── 05_database/
├── logs/
└── doc/
```

---

## 3. 套件骨架建議

```text
com.fsap.monitor
├── cli
│   ├── command
│   ├── runner
│   └── config
├── core
│   ├── ingest
│   ├── report
│   ├── viewsync
│   ├── monitor
│   ├── query
│   └── service
├── infra
│   ├── duckdb
│   ├── excel
│   ├── csv
│   ├── json
│   ├── filesystem
│   └── logging
├── web
│   ├── controller
│   ├── dto
│   └── advice
└── shared
    ├── model
    ├── exception
    └── util
```

---

## 4. `pom.xml` 核心設計

### 4.1 建議座標

```xml
<groupId>com.fsap</groupId>
<artifactId>fsap-monitor-util</artifactId>
<version>0.1.0-SNAPSHOT</version>
<name>fsap-monitor-util</name>
<description>FSAP monitor utility migrated from Python to Java</description>
```

### 4.2 Java 版本建議

建議：

- `Java 21`

理由：

- 目前 LTS
- Spring Boot 3.x 支援成熟
- 適合後續長期維護

若你們內部環境較保守，也可退到 `Java 17`，但第一選擇仍建議 21。

---

## 5. 依賴清單

### 5.1 必要依賴

| 類型 | 依賴 | 用途 |
| :--- | :--- | :--- |
| Boot | `spring-boot-starter` | 基礎啟動 |
| Web | `spring-boot-starter-web` | REST API |
| Template | `spring-boot-starter-thymeleaf` | 查詢頁與 HTMX 頁面 |
| Validation | `spring-boot-starter-validation` | DTO 驗證 |
| CLI | `info.picocli:picocli` | 命令列解析 |
| DB | `org.duckdb:duckdb_jdbc` | DuckDB JDBC |
| Excel | `org.apache.poi:poi-ooxml` | 讀寫 `.xlsx` |
| JSON | `com.fasterxml.jackson.core:jackson-databind` | JSON / JSONL |
| CSV | `org.apache.commons:commons-csv` | CSV 輸出 |
| Compress | `org.apache.commons:commons-compress` | 壓縮處理輔助，可選 |
| Logging | `spring-boot-starter-logging` | SLF4J + Logback |

### 5.2 測試依賴

| 類型 | 依賴 | 用途 |
| :--- | :--- | :--- |
| Test | `spring-boot-starter-test` | 單元/整合測試 |
| CLI Test | `info.picocli:picocli-codegen` | 可選，用於命令補充工具 |
| Assert | `org.assertj:assertj-core` | 可讀性較高的 assertion |

---

## 6. 建議的 `pom.xml` 範例

以下是第一版可用的基礎結構。

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.fsap</groupId>
    <artifactId>fsap-monitor-util</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>fsap-monitor-util</name>
    <description>FSAP monitor utility migrated from Python to Java</description>

    <properties>
        <java.version>21</java.version>
        <picocli.version>4.7.6</picocli.version>
        <duckdb.version>1.1.3</duckdb.version>
        <poi.version>5.3.0</poi.version>
        <commons.csv.version>1.11.0</commons.csv.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli</artifactId>
            <version>${picocli.version}</version>
        </dependency>

        <dependency>
            <groupId>org.duckdb</groupId>
            <artifactId>duckdb_jdbc</artifactId>
            <version>${duckdb.version}</version>
        </dependency>

        <dependency>
            <groupId>org.apache.poi</groupId>
            <artifactId>poi-ooxml</artifactId>
            <version>${poi.version}</version>
        </dependency>

        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-csv</artifactId>
            <version>${commons.csv.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>

            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 7. Plugin 建議

### 7.1 必要 plugin

| Plugin | 用途 |
| :--- | :--- |
| `spring-boot-maven-plugin` | 打包可執行 `jar` |
| `maven-compiler-plugin` | Java 編譯版本控制 |
| `maven-surefire-plugin` | 單元測試 |

### 7.2 建議補充 plugin

| Plugin | 用途 |
| :--- | :--- |
| `maven-failsafe-plugin` | 整合測試 |
| `maven-dependency-plugin` | 離線依賴預抓 |
| `versions-maven-plugin` | 版本盤點 |

---

## 8. 離線建置考量

### 8.1 建議做法

本節為早期 Maven 方案，現行做法已改為：

1. `GRADLE_USER_HOME=$PWD/.gradle-online ./gradlew clean bootJar testClasses zipOfflineMavenRepo`
2. 產出 `build/offline-maven-repo.zip`
3. 離線環境以 `gradle --offline -PofflineRepo=... bootJar` 或 `./gradlew --offline -PofflineRepo=... bootJar` 打包

### 8.2 離線環境交付物

建議至少包含：

1. `fsap-monitor-util-0.1.0-SNAPSHOT.jar`
2. 固定版本 `JDK`
3. `config/`
4. `03_sql_logic/`
5. `start.sh`
6. `start.bat`
7. 若需要，可附 `offline-repo/` 或 dependency cache

### 8.3 原則

- 執行時不能下載依賴
- 執行時不能要求安裝 Python
- 執行時不能要求安裝 Node.js

---

## 9. `application.yml` 建議

### 9.1 最低配置

```yaml
spring:
  thymeleaf:
    cache: false

server:
  port: 8080

fsap:
  paths:
    base-dir: .
    input-dir: 01_excel_input
    source-lake-dir: 02_source_lake
    sql-logic-dir: 03_sql_logic
    report-output-dir: 04_report_output
    database-file: 05_database/fsap-month-report.duckdb
    log-dir: logs
  views:
    max-rounds: 3
  web:
    readonly: true
```

### 9.2 Profile 建議

建議預留：

- `application.yml`
- `application-dev.yml`
- `application-prod.yml`

第一版即使先不用，也建議預留 profile 思維。

---

## 10. 啟動方式建議

### 10.1 CLI

```bash
java -jar fsap-monitor-util.jar ingest
java -jar fsap-monitor-util.jar sync-views
java -jar fsap-monitor-util.jar generate-report
java -jar fsap-monitor-util.jar update-monitor-data
java -jar fsap-monitor-util.jar doctor
```

### 10.2 Web

```bash
java -jar fsap-monitor-util.jar serve --port 8080
```

### 10.3 平台腳本

建議提供：

- `scripts/start.sh`
- `scripts/start.bat`

但腳本只負責：

- 設定 `JAVA_HOME`
- 切換工作目錄
- 呼叫 `java -jar`

不要再讓腳本承擔套件安裝責任，這是和目前 Python `.venv` 模式最大的差異之一。

---

## 11. 測試骨架建議

### 11.1 測試分類

| 類型 | 位置 | 說明 |
| :--- | :--- | :--- |
| 單元測試 | `src/test/java/...` | 純 service / util |
| 整合測試 | `src/test/java/...` | DuckDB、SQL、Excel I/O |
| Web 測試 | `src/test/java/...` | controller / API |

### 11.2 第一批應先補的測試

1. Excel 檔名解析
2. JSONL.gz 輸出格式
3. DuckDB 連線與 SQL 載入
4. 報表輸出目錄建立
5. CSV 輸出格式

---

## 12. 第一版不建議納入的東西

先不要加：

1. 多 Maven 模組
2. Docker 建置鏈
3. 前後端分離
4. ORM/JPA
5. 複雜的資料來源抽象層

理由：

- 這個專案核心是檔案流程 + DuckDB SQL，不是典型資料庫 CRUD 系統
- ORM 只會增加複雜度，不會帶來實質好處

---

## 13. 最終建議

第一版 `pom.xml` 應該只做三件事：

1. 能穩定編譯
2. 能打成單一可執行 jar
3. 能把 CLI + Web + DuckDB + Excel 能力都裝進去

也就是說，現在最正確的方向不是追求最漂亮的企業級拆模組，而是先交付一個 **可取代 Python、可離線運行、可持續擴充** 的骨架。

---

## 14. 落地結果與後續版本

本文件中的第一版骨架已完成落地：

1. 早期 `pom.xml` 已由 `build.gradle` 取代
2. `src/main/java` 與 `src/main/resources` 骨架已建立
3. 目前可打包單一可執行 `jar`
4. CLI 與 Web 已共用同一套 service / DuckDB / 路徑處理

後續版本重點建議如下：

1. `v1.1`：在現有骨架上補 Web 任務操作頁，直接觸發 `doctor`、`sync-views`、`ingest`、`generate-report`、`update-monitor-data`
2. `v1.2+`：再處理多查詢工作區、結果匯出 UI、schema 細節與啟動包裝等周邊功能

---

*文件更新於: 2026-04-22*
