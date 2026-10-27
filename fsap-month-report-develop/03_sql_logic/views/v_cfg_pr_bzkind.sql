CREATE OR REPLACE VIEW v_cfg_pr_bzkind AS
-- 程式代號對照表（PR_CFG 最新快照）：名稱、業務分類 PR_BZKIND、交易類別 PR_CATEGORY（update／query）
-- PR_TPKIND：2＝查詢（query）、1＝交易（update）；其他值或空值 PR_CATEGORY 為 NULL，由 generate-report 前置檢查擋下
-- PR_CFG 每天是完整快照，只取 _dt 最新的一份；以 columns 指定欄位，舊快照沒有 PR_TPKIND 時該欄為 NULL
WITH src AS (
  SELECT * FROM read_json('02_source_lake/PR_CFG/PR_CFG-*.jsonl.gz',
    format = 'newline_delimited',
    columns = {PR_ID: 'VARCHAR', PR_NAME: 'VARCHAR', PR_BZKIND: 'VARCHAR', PR_TPKIND: 'VARCHAR', _dt: 'VARCHAR', _ingest_ts: 'BIGINT'})
)
SELECT
  trim(PR_ID)                                        AS PR_ID,
  trim(PR_NAME)                                      AS PR_NAME,
  NULLIF(trim(PR_BZKIND), '')                        AS PR_BZKIND,
  TRY_CAST(TRY_CAST(PR_TPKIND AS DOUBLE) AS INTEGER) AS PR_TPKIND,
  CASE TRY_CAST(TRY_CAST(PR_TPKIND AS DOUBLE) AS INTEGER)
    WHEN 2 THEN 'query'
    WHEN 1 THEN 'update'
  END                                                AS PR_CATEGORY,
  _dt                                                AS snapshot_dt
FROM src
WHERE _dt = (SELECT MAX(_dt) FROM src)
QUALIFY ROW_NUMBER() OVER (
    PARTITION BY trim(PR_ID)
    ORDER BY _ingest_ts DESC
) = 1;
