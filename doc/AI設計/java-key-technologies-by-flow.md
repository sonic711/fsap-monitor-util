# Java Execution Flows

Checked against source on 2026-10-28. Class names below are relative to `src/main/java/com/fsap/monitor/`.

## Startup and Paths

`FsapApplication` chooses CLI or Web mode, translates serve flags and uses Spring Boot `ApplicationHome` to locate the outer executable JAR, including nested-JAR URLs. Its home and config directory are optional additional locations; standard working-directory locations still apply. Use an explicit `--spring.config.additional-location=file:/.../application.yml` before the command for deterministic deployment.

Classpath application.yml contains stable defaults only. Root config/application.yml owns operational settings, ingest rules and report lists and is not packaged. Report generation validates explicit presence of target-bzkinds, all-sheets and reports before resolving parameters, creating a batch or opening the DB. Explicit empty lists are allowed; missing lists fail rather than silently falling back to ALL. Other commands and Web initialization remain available for diagnosis.

`ProjectPathService` resolves configured data paths against `fsap.paths.base-dir` and rewrites recognized SQL path literals to absolute paths. It does not relocate Spring config or all CLI path overrides.

## Input

`InputUploadService` accepts nonempty `.xlsx` uploads, writes `.uploading`, then replaces the target input file. It does not enforce the ingest filename pattern or ingest automatically.

`IngestService` scans matching names in the input directory, newest filename first. Positive `limit` is applied before existing-output checks. The first regex capture supplies `_dt`. It exports configured sheets present in the workbook, skipping existing sheet outputs unless forced.

POI reads cached formula results; it does not recalculate formulas. Jackson writes original fields plus `_file`, `_sheet`, `_dt`, `_ingest_ts` through GZIP. No physical transaction tables are populated. Failed writes can leave incomplete files, so use date-scoped `--force` for recovery.

## SQL and Views

`DuckDbConnectionFactory` opens the configured local database file. `ViewSyncService` scans view SQL, orders dependencies, drops matching existing views and retries creation. No-progress rounds stop; a failed sync is not a rollback of the previous view set.

The effective round ceiling is at least the number of view files, even when `max-rounds` is lower. `generate-report` loads optional macros then syncs views; monitor export syncs views but does not load report macros.

SQL execution uses JDBC `Statement`, not `JdbcTemplate` or parameter-bound `PreparedStatement`. Report placeholder rendering and path rewriting do not make arbitrary SQL files safe. External SQL and config are trusted deployment assets.

## Reports

1. Resolve `ReportGenerationRequest`; create batch and `report-params.json`.
2. Load macros and views; optionally materialize the five allowlisted report views as connection-local TEMP TABLEs; validate latest PR_CFG categories and log classification summary.
3. Run ALL plus selected available business kinds. Every pass executes all report SQL in numeric-prefix order.
4. Save ALL CSV/full workbook under `SUM/`; category CSV/full workbooks under category directories.
5. `ComparisonWorkbookPlanner` selects already computed tables by configuration.
6. Publish the cross-kind workbook at batch root as `維運月度報表_MM月彙總_<yyyyMMddHHmm>.xlsx`.

Default composition is 10 overview sheets plus 8 per each of 7 kinds (including FAS): 66 sheets. Overview TOP6 update/query sheets use ALL results from reports 7.1/7.2. Daily/peak/history-peak sheets use SQL Pivot results. There is no `REPORT/` directory or configurable comparison filename anymore. ALL-only or disabled/empty comparison falls back to a copy of the full ALL workbook at the root.

POI creates one style set per workbook and safe sheet names; Commons CSV writes UTF-8 BOM CSVs. Only recognized empty-projection Pivot errors become Message sheets. Other SQL failures cause a failing command; partial artifacts can remain.

`ReportViewSnapshot` materializes available views in dependency order: v_cfg_pr_bzkind, v_rt_pr_hh24_clean, v_monthly_transaction_stats, v_prod_monitor_daily_count, v_prod_monitor_hourly_count. TEMP TABLEs shadow only this connection's lookups, retain the full original data range and disappear on close. Existing permanent tables and unrelated views are not materialized. `fsap.report.materialize-views` defaults to true in the JAR and can be overridden to false. Successful generation logs total elapsed time including materialization and output. See the human performance guide for measured results and known nondeterministic SQL ties/rounding; do not claim byte-identical workbooks.

## SFTP

`SftpInputDownloadService` searches the backup tree for the requested daily filename. Successful download writes `logs/latest-sftp-download.json`.

`SftpReportUploadService` selects batches with 12-digit directory names by directory mtime, then the latest root `.xlsx` by file mtime. It does not recurse into SUM/category directories, infer the reporting month or validate batch success. Destination defaults to the latest download metadata. Existing remote filenames are rejected unless overwrite is requested.

`Jenkinsfile` performs daily download/ingest/report; `Jenkinsfile.upload` regenerates then uploads. Both configure `/app/fsap-monitor-util/tmp`. Job-local concurrency prevention does not coordinate the two jobs or a Web JVM.

## Monitor and Query

`MonitorDataExportService` uses config tasks to export daily (latest available date minus 10 days) or hourly (minus 4 days), at most 10000 rows per task. It uses the configured application DB, not JSON `db_path`. Empty results leave old outputs untouched.

`ArtifactBrowseService` reads CSV for the monitor table/chart UI. The SQL console is a single editor with JSON results; its successful query history is JSONL on disk. Schema browsing lists names and types, not columns.

Readonly disables task/upload endpoints and uses a SQL-prefix check in `QueryService`; it is not a database-enforced security boundary. No authentication or durable job queue is implemented.

See [module/API map](java-module-and-command-design.md), [report rules](java-report-parameterization-plan.md) and [limitations](java-remaining-features.md).
