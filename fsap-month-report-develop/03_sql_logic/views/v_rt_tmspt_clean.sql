CREATE OR REPLACE VIEW v_rt_tmspt_clean AS
WITH src AS (
  SELECT
    PR_ID,
    CALDY,
    AVG_TM,
    _file, _sheet, _dt, _ingest_ts,
    strptime(_dt, '%Y%m%d')::DATE AS file_dt
  FROM read_json_auto(
    '02_source_lake/RT_TMSPT/RT_TMSPT-*.jsonl.gz'
  )
),
cal AS (
  SELECT
    *,
    -- CALDY: 'MMDD' → month/day
    TRY_CAST(substr(CALDY, 1, 2) AS INT) AS cal_month,
    TRY_CAST(substr(CALDY, 3, 2) AS INT) AS cal_day
  FROM src
),
mk AS (
  SELECT
    *,
    -- 先用 file_dt 的年份組日期
    make_date(EXTRACT(year FROM file_dt)::INT, cal_month, cal_day) AS cal_dt_guess
  FROM cal
  -- 提早過濾無效日期，減少後續計算量
  WHERE cal_month IS NOT NULL AND cal_day IS NOT NULL
),
logic AS (
  SELECT
    *,
    -- 集中處理日期邏輯：若組出來的日期 > 報表日，表示跨年 → 年份 -1
    CASE
      WHEN cal_dt_guess > file_dt THEN cal_dt_guess - INTERVAL 1 YEAR
      ELSE cal_dt_guess
    END AS tx_dt
  FROM mk
)
SELECT
  logic.PR_ID,

  -- PR_ID normalize
  CASE
    WHEN regexp_matches(logic.PR_ID, '^[A-Z]\\d+$') THEN
      regexp_replace(logic.PR_ID, '^([A-Z])(\\d+)$', '\\1' || lpad('\\2', 4, '0'))
    ELSE logic.PR_ID
  END AS pr_id_norm,

  CALDY,
  AVG_TM AS avg_tm_sec,
  AVG_TM * 1000.0 AS avg_tm_ms,

  tx_dt,
  strftime(tx_dt, '%Y-%m-%d') AS tx_dt_str,

  file_dt,
  strftime(file_dt, '%Y-%m-%d') AS file_dt_str,

  EXTRACT(year  FROM tx_dt)::INT AS tx_year,
  EXTRACT(month FROM tx_dt)::INT AS tx_month,
  EXTRACT(day   FROM tx_dt)::INT AS tx_day,
  strftime(tx_dt, '%Y-%m') AS tx_yyyymm,

  COALESCE(bz.PR_BZKIND, '其他/待分類') AS pr_bzkind,
  _file, _sheet, _dt, _ingest_ts
FROM logic
LEFT JOIN v_cfg_pr_bzkind bz
  ON trim(logic.PR_ID) = bz.PR_ID
-- [新增] 使用 QUALIFY 進行去重
QUALIFY ROW_NUMBER() OVER (
    PARTITION BY tx_dt, logic.PR_ID
    ORDER BY file_dt DESC, _dt DESC, _file DESC
) = 1;
