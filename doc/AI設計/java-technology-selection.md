# Implemented Technology Baseline

Checked against `build.gradle` and source on 2026-10-28. This replaces the original alternative-selection proposal; it describes what is implemented, not a new recommendation.

## Stack

| Area | Current implementation |
| --- | --- |
| Build/runtime | Gradle Wrapper 8.13, Java toolchain/release 17, one Spring Boot executable JAR |
| Framework | Spring Boot 3.5.14; MVC, Thymeleaf, validation |
| CLI | Picocli 4.7.6, Spring-managed commands |
| Database | DuckDB JDBC 1.5.2.0, local file plus external-file views |
| Excel | Apache POI 5.4.0; XSSF output |
| JSON/compression | Jackson, JDK GZIP |
| CSV | Commons CSV 1.11.0 |
| Transfer | JSch 0.1.55, Commons Net 3.10.0, Jasypt 1.9.3 |
| UI | Native JavaScript and bundled Chart.js; no React/Vue/HTMX build |
| Logs | SLF4J/Logback console plus service-specific file writes |

`build.gradle` overrides Spring Framework, Tomcat, Thymeleaf, Jackson BOM and Commons Lang versions. Do not claim all dependency versions come unchanged from Spring Boot. The build is the authoritative version source; build success does not establish absence of CVEs.

## Deployment Model

- Running a built JAR needs Java 17, external config and the data/SQL directory, not Gradle, Python, Node.js or a running DuckDB server.
- SQL and reference data remain external to the JAR.
- Root config/application.yml contains deployment and business settings, including all report sheet definitions; classpath application.yml retains only stable internal defaults. Both files must be considered when testing configuration. Report generation rejects missing external report lists.
- Offline compilation needs JDK 17, Gradle distribution, original sources and the exported Maven-layout repository.
- Buildscript plugin dependencies and application dependencies are exported; the Gradle distribution is a separate ZIP.
- `resolved` exports JAR/AAR files for resolved module coordinates but retains all cached POM/module files. `all-cache` exports all supported files already in module cache. Neither is a complete Gradle-user-home backup.
- No universal Java `start.sh`/`start.bat` or automatic production deployment is shipped. Jenkins executes the JAR already installed on the target host.

## Deliberate Boundaries

The Java core has replaced day-to-day Python ETL/report/query operations. Historical Python documents do not imply their scripts are available in the current checkout. Old code must be obtained from the corresponding archive or Git history before reproduction.

Use the existing core service layer for CLI and Web changes. This project is not JPA-based and does not use JdbcTemplate for report SQL.

## References

- [Execution flows](java-key-technologies-by-flow.md)
- [Module and API map](java-module-and-command-design.md)
- [Offline build](../人類部署與建置/Gradle離線建置.md)
- [Production update](../人類部署與建置/正式環境更新步驟.md)
