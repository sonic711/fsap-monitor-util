CREATE OR REPLACE VIEW v_rt_cnt_weekly AS
WITH base AS (
  SELECT
    年,
    年週,
    交易類型,
    CAST(交易日期 AS DATE) AS tx_date,
    交易總筆數,
    成功筆數,
    失敗筆數,
    逾時筆數
  FROM v_rt_cnt_daily
)
SELECT
  年,
  年週,

  -- 固定週界線（ISO week）
  strptime(年週 || '-1', '%G-%V-%u')::DATE AS 週開始日,
  strptime(年週 || '-7', '%G-%V-%u')::DATE AS 週結束日,

  -- 實際資料涵蓋範圍
  MIN(tx_date) AS 資料開始日,
  MAX(tx_date) AS 資料結束日,
  交易類型,
  COUNT(DISTINCT tx_date) AS 有資料日數,

  -- 交易量
  SUM(交易總筆數)::BIGINT AS 週交易總筆數,
  ROUND(SUM(交易總筆數) * 1.0 / NULLIF(COUNT(DISTINCT tx_date), 0), 0)::BIGINT AS 日均交易量_有資料日,
  ROUND(SUM(交易總筆數) / 7.0, 0)::BIGINT AS 日均交易量_週平均,

  MAX(交易總筆數)::BIGINT AS 單日峰值交易量,
  arg_max(tx_date, 交易總筆數) AS 單日峰值日期,

  -- 成功/失敗
  SUM(成功筆數)::BIGINT AS 週成功筆數,
  SUM(失敗筆數)::BIGINT AS 週失敗筆數,
  SUM(逾時筆數)::BIGINT AS 週逾時筆數,

  ROUND(
    SUM(成功筆數) * 100.0 / NULLIF(SUM(交易總筆數), 0),
    2
  ) AS 週成功率百分比
FROM base
GROUP BY 年, 年週, 交易類型;
