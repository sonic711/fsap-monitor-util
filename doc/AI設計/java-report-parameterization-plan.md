# Report Parameters and Output Contract

Checked against source on 2026-10-28. The filename is retained for existing links; this is the implemented contract, not a pending plan.

## Model and Defaults

`ReportGenerationRequest` is shared by CLI and Web DTO conversion. `ReportParameterDefaultsService` normalizes, derives and validates values.

Business settings now live exclusively in root config/application.yml, not classpath application.yml. `FsapProperties.Report.validateConfiguration()` distinguishes absent lists from explicit empty lists and rejects missing target-bzkinds, all-sheets or reports before generation. Do not restore hardcoded report fallbacks in the JAR; explicit empty configuration still supports ALL-only/disabled comparison.

| Input | Actual behavior |
| --- | --- |
| `targetMonth` | Explicit value wins; otherwise derive from rangeEndDate, rangeStartDate, rangeEndTime, rangeStartTime, in that order; otherwise previous month in JVM default timezone |
| `rangeStartDate` / `rangeEndDate` | Target month boundaries if absent; validate end >= start |
| `rangeStartTime` / `rangeEndTime` | Date boundaries at 00:00 / 23:59 if absent; accept space or T separators; validate ordering |
| `historyStartMonth` | Fixed default 2025-09, not target month minus seven months |
| `historyEndMonth` | Defaults to targetMonth; validate ordering |
| `targetBzkinds` | null uses configuration; empty selects ALL only; trim/deduplicate before availability selection |
| `timestamp` | Defaults to JVM-local yyyyMMddHHmm; custom text is not validated as a safe 12-digit batch name |
| `continueOnError` | Continue SQL processing; any report failure still makes generation fail |

`previousTargetMonth`, `previousRangeStartTime` and `previousRangeEndTime` always describe the complete previous month of targetMonth, not a shifted copy of an arbitrary custom interval.

## SQL Parameters

The renderer substitutes known `${key}` names from the request map; unknown/unresolved keys fail. `targetBzkind` is supplied by each execution pass. Date/time inputs are parsed before rendering. This is not JDBC parameter binding or general-purpose SQL sanitization.

| SQL group | Effective inputs |
| --- | --- |
| 1, 2/2.1, 3/3.1, 7/7.1/7.2, 8, 8.3 | targetMonth; 8.3 also previousTargetMonth |
| 1.1, 1.1.1, 1.1.4 | rangeStartDate / rangeEndDate |
| 1.1.2, 1.1.3 | targetMonth for whole-system peak selection |
| 4/4.1, 5/5.1, 6/6.1 | historyStartMonth / historyEndMonth; start is a hidden comparison baseline |
| 8.1, 8.2 | rangeStartTime / rangeEndTime and derived previous-month full interval |

All transaction reports accept targetBzkind; CPU/memory SQL does not filter by business kind. Report 8 is a full-month view query and does not accept custom time intervals. There is no targetCategory request parameter.

## Classification

`v_cfg_pr_bzkind` uses the greatest `_dt` across PR_CFG files, then latest ingest timestamp per PR_ID. PR_TPKIND 1 maps to update; 2 maps to query. Missing/invalid category data stops generation. The snapshot is not selected as of targetMonth.

ALL includes unmatched transaction IDs; category summaries use PR_BZKIND. Reports 7/8 require metadata joins, so unconfigured IDs may contribute to ALL denominators but not listed rows. FSAP is one category, not a synonym for ALL.

`BzkindSelection` sorts available requested kinds for execution; `combined-workbook.bzkinds` controls comparison order, falling back to executed kinds only when its list is empty.

## Output

- Batch root: final `維運月度報表_MM月彙總_<timestamp>.xlsx` and `report-params.json`.
- `SUM/`: ALL full workbook and every report CSV; categories keep equivalent outputs under their codes.
- Comparison defaults: 10 ALL sheets (including TOP6 update/query from 7.1/7.2) plus 8 sheets × 7 kinds (including FAS) = 66 sheets.
- Daily/peak/history peak tables use reports 2.1, 3.1, 5.1 and 6.1; history monthly totals retain report 4.
- No REPORT directory; `combined-workbook.filename` is ignored/removed.
- No executed kinds, disabled comparison or no selected tables: root final workbook is copied from ALL.
- ReportFileResult failures and partial XLSX files must not be mistaken for successful publication.

Root final workbook is used by CLI/Web result paths and default SFTP selection. Reusing a timestamp does not clean stale outputs. SFTP automatic selection recognizes only 12-digit batch names.

Human reference: [report SQL](../人類報表與資料/報表SQL說明.md), [formulas](../人類報表與資料/報表公式與口徑.md), [output layout](../人類報表與資料/報表輸出目錄.md).
