CREATE OR REPLACE VIEW v_rt_cnt_daily_with_tmspt AS
WITH t AS (
  SELECT
    CAST(tx_dt_str AS DATE) AS tx_date,
    PR_ID AS 交易類型,
    AVG(avg_tm_sec) AS avg_tm_sec,   -- 保險：就算有重複也會被收斂
    AVG(avg_tm_ms)  AS avg_tm_ms
  FROM v_rt_tmspt_clean
  GROUP BY 1, 2
)
SELECT
  cnt.*,
  t.avg_tm_sec AS "平均交易時間(秒)",
  t.avg_tm_ms  AS "平均交易時間(毫秒)"
FROM v_rt_cnt_daily cnt
LEFT JOIN t
  ON CAST(cnt.交易日期 AS DATE) = t.tx_date
 AND cnt.交易類型 = t.交易類型;
