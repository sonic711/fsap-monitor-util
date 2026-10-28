# Java Modules and Commands

Current implementation reference, checked against source on 2026-10-28. This is a single Gradle project, not a multi-module Maven design.

## Boundaries

| Package | Implemented responsibility |
| --- | --- |
| `cli` | Picocli options and exit codes; delegates to core services |
| `web` | Spring MVC controllers, task requests, Thymeleaf page and downloads |
| `core/ingest` | Multipart storage and Excel-to-JSONL.GZ conversion |
| `core/viewsync` | Dependency ordering, dropping and rebuilding external SQL views |
| `core/report` | Parameter defaults, PR_CFG checks, classified report passes and final workbook |
| `core/sftp` | Input search/download, metadata and report upload orchestration |
| `core/query`, `core/artifact`, `core/monitor`, `core/task` | Queries, artifact browsing, monitor exports and in-process task workflow |
| `infra` | `FsapProperties` and DuckDB JDBC connection creation |
| `sftp` | Transfer adapters, connection settings and encryption helper |

## Entry Points

`FsapApplication` separates leading Spring property arguments from the CLI command. Only `serve` starts a servlet application; other commands run through `PicocliRunner` and exit.

Use `--fsap.*=...`, `--spring.*=...` and other supported Spring properties before the command. JVM `-D...` options belong before `-jar`. External config and data base-dir are different concepts.

| Command | Core implementation |
| --- | --- |
| `doctor` | `EnvironmentCheckService` |
| `download-input` | `SftpInputDownloadService` |
| `ingest` | `IngestService` |
| `sync-views` | `ViewSyncService` |
| `generate-report` | `ReportGenerationService` |
| `upload-report` | `SftpReportUploadService` |
| `update-monitor-data` | `MonitorDataExportService` |
| `serve` | `FsapApplication`; `ServeCommand` is descriptive, not the server lifecycle owner |

Option definitions are in `cli/command/`. Do not invent `--db-path`, `--target-category`, `--out-dir` or export flags; those are not implemented CLI options.

## Web Contract

- One template: `src/main/resources/templates/query.html`; Operations, Monitor Dashboard and Query Console.
- Task API: `POST /api/tasks/{doctor,ingest,sync-views,generate-report,update-monitor-data}`.
- Upload input: `POST /api/tasks/ingest/upload`, multipart field `files`.
- Read APIs: `GET /api/tasks`, `/api/tasks/ingest/files`, `/api/tasks/report-batches`, `/api/tasks/monitor-files`, `/api/tasks/monitor-data?limit=10000`.
- Query API: `POST /api/query`; schema names/types: `GET /api/schema`; liveness only: `GET /health`.
- Downloads: `GET /downloads/file?path=...`, relative to report-output root, not arbitrary files or input Excel.
- SFTP commands have no equivalent task API.

`TaskExecutionService` serializes Web tasks in one process. Doctor → Ingest → Sync Views → Generate Report is enforced only for Web tasks. Earlier successful steps invalidate later workflow state; restart clears task history. CLI/Jenkins/other JVMs do not share this lock.

## References

- [Execution flows](java-key-technologies-by-flow.md)
- [Report parameters](java-report-parameterization-plan.md)
- [Current limitations](java-remaining-features.md)
- [Human operations](../人類操作/啟動與產生報表.md)
