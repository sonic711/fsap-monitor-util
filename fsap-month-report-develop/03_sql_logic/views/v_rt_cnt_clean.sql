CREATE OR REPLACE VIEW v_rt_cnt_clean AS
WITH src AS (
  SELECT
    CALDY, PR_ID,
    "總筆數" AS total_cnt, "成功" AS ok_cnt, "失敗" AS fail_cnt,
    "失敗但沖正成功" AS rev_ok_cnt, TIMEOUT AS timeout_cnt, "其他錯誤" AS other_err_cnt,
    "首筆交易" AS first_tx_raw, "尾筆交易" AS last_tx_raw,
    _file, _sheet, _dt, _ingest_ts,
    strptime(_dt, '%Y%m%d')::DATE AS file_dt
  FROM read_json_auto('02_source_lake/RT_CNT/RT_CNT-*.jsonl.gz')
),
ts AS (
  SELECT *,
    try_strptime(first_tx_raw, '%Y-%m-%dT%H:%M:%S.%f') AS tx_ts_first,
    try_strptime(last_tx_raw,  '%Y-%m-%dT%H:%M:%S.%f') AS tx_ts_last
  FROM src
),
cal AS (
  SELECT *,
    TRY_CAST(substr(CALDY, 1, 2) AS INT) AS cal_month,
    TRY_CAST(substr(CALDY, 3, 2) AS INT) AS cal_day
  FROM ts
),
mk AS (
  SELECT *,
    CASE
      WHEN cal_month IS NOT NULL AND cal_day IS NOT NULL
        THEN make_date(EXTRACT(year FROM file_dt)::INT, cal_month, cal_day)
      ELSE NULL
    END AS cal_dt_guess
  FROM cal
),
final AS (
  SELECT *,
    CAST(tx_ts_first AS DATE) AS tx_dt_from_ts,
    CASE
      WHEN cal_dt_guess IS NULL THEN NULL
      WHEN cal_dt_guess > file_dt THEN cal_dt_guess - INTERVAL 1 YEAR
      ELSE cal_dt_guess
    END AS tx_dt_from_caldy
  FROM mk
)
-- [修改點] 這裡直接進行 SELECT 並使用 QUALIFY 去重，移除了 dedup CTE
SELECT
  CALDY,
  PR_ID,
  total_cnt, ok_cnt, fail_cnt, rev_ok_cnt, timeout_cnt, other_err_cnt,
  file_dt,
  strftime(file_dt, '%Y-%m-%d') AS file_dt_str,
  tx_ts_first, tx_ts_last, first_tx_raw, last_tx_raw,

  -- 將 key 的計算移到這裡
  COALESCE(tx_dt_from_ts, tx_dt_from_caldy) AS tx_dt,
  strftime(COALESCE(tx_dt_from_ts, tx_dt_from_caldy), '%Y-%m-%d') AS tx_dt_str,

  EXTRACT(year  FROM COALESCE(tx_dt_from_ts, tx_dt_from_caldy))::INT AS tx_year,
  EXTRACT(month FROM COALESCE(tx_dt_from_ts, tx_dt_from_caldy))::INT AS tx_month,
  EXTRACT(day   FROM COALESCE(tx_dt_from_ts, tx_dt_from_caldy))::INT AS tx_day,
  strftime(COALESCE(tx_dt_from_ts, tx_dt_from_caldy), '%Y-%m') AS tx_yyyymm,

  CASE
    WHEN CALDY IS NULL THEN NULL
    ELSE (CALDY = strftime(COALESCE(tx_dt_from_ts, tx_dt_from_caldy), '%m%d'))
  END AS caldy_match_txdt,

  _file, _sheet, _dt, _ingest_ts

FROM final
QUALIFY ROW_NUMBER() OVER (
    PARTITION BY COALESCE(tx_dt_from_ts, tx_dt_from_caldy), PR_ID
    ORDER BY file_dt DESC, _dt DESC, _file DESC
) = 1;