CREATE OR REPLACE VIEW v_rt_node_hh24_clean AS
WITH src AS (
  SELECT
    trim(PR_ID)   AS PR_ID,
    trim(NODE_ID) AS NODE_ID,
    CALTM,
    AVG_TM,
    CNT,
    _file, _sheet, _dt, _ingest_ts,
    strptime(_dt, '%Y%m%d')::DATE AS file_dt
  FROM read_json_auto(
    '02_source_lake/RT_NODE_HH24/RT_NODE_HH24-*.jsonl.gz'
  )
),
parts AS (
  SELECT
    *,
    TRY_CAST(substr(CALTM, 1, 2) AS INT) AS cal_month,
    TRY_CAST(substr(CALTM, 3, 2) AS INT) AS cal_day,
    TRY_CAST(substr(CALTM, 6, 2) AS INT) AS cal_hour
  FROM src
),
mk AS (
  SELECT
    *,
    CASE
      WHEN cal_month IS NOT NULL AND cal_day IS NOT NULL
        THEN make_date(EXTRACT(year FROM file_dt)::INT, cal_month, cal_day)
      ELSE NULL
    END AS cal_dt_guess
  FROM parts
),
final AS (
  SELECT
    *,
    CASE
      WHEN cal_dt_guess IS NULL THEN NULL
      WHEN cal_dt_guess > file_dt THEN cal_dt_guess - INTERVAL 1 YEAR
      ELSE cal_dt_guess
    END AS tx_dt
  FROM mk
)
SELECT
  PR_ID,
  NODE_ID,
  CALTM,

  -- 指標
  AVG_TM AS avg_tm_sec,
  AVG_TM * 1000 AS avg_tm_ms,
  CNT AS tx_cnt,

  -- 報表日
  file_dt,
  strftime(file_dt, '%Y-%m-%d') AS file_dt_str,

  -- 交易日
  tx_dt,
  strftime(tx_dt, '%Y-%m-%d') AS tx_dt_str,

  -- 時間維度
  EXTRACT(year  FROM tx_dt)::INT AS tx_year,
  EXTRACT(month FROM tx_dt)::INT AS tx_month,
  EXTRACT(day   FROM tx_dt)::INT AS tx_day,
  strftime(tx_dt, '%Y-%m') AS tx_yyyymm,
  cal_hour AS tx_hour,

  -- 小時 timestamp（超好用）
  (tx_dt + cal_hour * INTERVAL 1 HOUR) AS tx_hour_ts,
  strftime((tx_dt + cal_hour * INTERVAL 1 HOUR), '%Y-%m-%d %H:00:00') AS tx_hour_ts_str,

  -- 檢核
  CASE
    WHEN tx_dt IS NULL THEN NULL
    ELSE (substr(CALTM, 1, 4) = strftime(tx_dt, '%m%d'))
  END AS caltm_match_txdt,

  -- meta
  _file, _sheet, _dt, _ingest_ts
FROM final;
