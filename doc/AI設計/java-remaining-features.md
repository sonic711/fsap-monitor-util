# Implemented Scope and Remaining Gaps

Checked against source on 2026-10-28. Version labels such as v1.0/v1.1/v1.2 were planning labels, not a supported release contract, and are omitted here.

## Implemented

- Eight CLI commands: doctor, download-input, ingest, sync-views, generate-report, upload-report, update-monitor-data and serve.
- External settings for packaged CLI and Web execution; Gradle Web/debug tasks and Eclipse plugin.
- Stable classpath defaults separated from external deployment/business rules; fail-fast missing-report-list validation and nested-JAR home resolution.
- Excel → JSONL.GZ → external DuckDB views → classified reports.
- Latest PR_CFG-driven names, business kinds and update/query categories; validation before reporting.
- Six default kinds including FSAP, 56-sheet cross-kind final workbook, SUM outputs and root monthly workbook.
- SQL Pivot selection for daily, peak-day/hour and historical peak sheets.
- Daily generation and monthly generation/upload Jenkins pipelines, each with JVM tmpdir settings.
- Web upload and ordered single-process tasks; report downloads and monitor CSV tables/charts.
- One SQL editor, JSON results, successful-query history on disk and table/view name/type listing.
- Reusable offline Maven export tasks with resolved/all-cache modes.

## Not Implemented

| Gap | Current boundary |
| --- | --- |
| Multi-user access control | No application authentication/roles; readonly is only a task gate and SQL-prefix check |
| Cross-process coordination | Web queue is local to one JVM; Jenkins jobs and CLI can still contend for the same DB |
| Durable task history | Web task status/workflow state resets at restart |
| Full SQL console | No 10 editor slots, `sql_history.json` slot restoration, SQL-result CSV/JSONL export or column-type browser |
| Generic request audit | Successful SQL is logged, but no complete method/path/status/duration request audit |
| Arbitrary report category option | No `--target-category`; update/query filters remain SQL definitions |
| Batch publication validation | Upload selects by mtime, not a completed/successful manifest |
| Batch-safe naming | Minute-level timestamps can collide; reruns can overwrite files |
| Historical classification snapshots | Reporting uses the latest snapshot even for historical reporting months |
| Automated deployment | Push does not deploy JAR/SQL/config/data to the target host |
| Complete offline portability | Unsupported caches, distributions and missing artifacts are not created by cache conversion |

These are observed limits, not a commitment to implement them. Do not document proposed features as available commands or verified security guarantees.

## Verification Baseline

Run `./gradlew --offline test bootJar` with JDK 17 and prepared dependencies. Use isolated data for ingest/report tests. SFTP mocks verify orchestration and byte preservation, not production connectivity or permissions.

See [human maintenance guide](../人類操作/專案維護指引.md) for acceptance checks.
