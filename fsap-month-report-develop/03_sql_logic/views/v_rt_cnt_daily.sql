CREATE OR REPLACE VIEW v_rt_cnt_daily AS

SELECT

  strftime(tx_dt, '%Y') AS 年,

  strftime(tx_dt, '%Y-%m') AS 年月,

  -- 年週（ISO）
  strftime(tx_dt, '%G-%V') AS 年週,

  tx_dt_str              AS 交易日期,

  CASE EXTRACT(dow FROM tx_dt)
    WHEN 1 THEN '一'
    WHEN 2 THEN '二'
    WHEN 3 THEN '三'
    WHEN 4 THEN '四'
    WHEN 5 THEN '五'
    WHEN 6 THEN '六'
    WHEN 0 THEN '日'
  END AS 星期,

  PR_ID                  AS 交易類型,
  SUM(total_cnt)         AS 交易總筆數,
  SUM(ok_cnt)            AS 成功筆數,
  SUM(fail_cnt)          AS 失敗筆數,
  SUM(timeout_cnt)       AS 逾時筆數,
  ROUND(
    SUM(ok_cnt) * 100.0 / NULLIF(SUM(total_cnt), 0),
    2
  ) AS 成功率百分比
FROM v_rt_cnt_clean
WHERE (1 = 1)
--AND PR_ID IN ('FAC2FAS', 'G61')
GROUP BY 1, 2, 3, 4, 5, 6
ORDER BY 1, 2, 3, 4, 5, 7 DESC;
