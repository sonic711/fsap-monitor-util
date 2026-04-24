CREATE OR REPLACE VIEW v_rt_cnt_monthly AS
WITH base AS (
  SELECT
    年,
    年月,
    交易類型,
    CAST(交易日期 AS DATE) AS tx_date,
    交易總筆數,
    成功筆數,
    失敗筆數,
    逾時筆數
  FROM v_rt_cnt_daily
),
m AS (
  SELECT
    年,
    年月,
    

    -- 月界線（固定）
    strptime(年月 || '-01', '%Y-%m-%d')::DATE AS 月開始日,
    (date_trunc('month', strptime(年月 || '-01', '%Y-%m-%d')::DATE) + INTERVAL 1 MONTH - INTERVAL 1 DAY)::DATE AS 月結束日,

    -- 實際資料涵蓋範圍（該交易類型該月有出現的日期）
    MIN(tx_date) AS 資料開始日,
    MAX(tx_date) AS 資料結束日,
    
    交易類型,
    COUNT(DISTINCT tx_date) AS 有資料日數,

    -- 月交易量
    SUM(交易總筆數)::BIGINT AS 月交易總筆數,
    ROUND(SUM(交易總筆數) * 1.0 / NULLIF(COUNT(DISTINCT tx_date), 0), 0)::BIGINT AS 日均交易量_有資料日,
    ROUND(SUM(交易總筆數) * 1.0 / NULLIF(EXTRACT(day FROM (date_trunc('month', strptime(年月 || '-01', '%Y-%m-%d')::DATE) + INTERVAL 1 MONTH - INTERVAL 1 DAY)), 0), 0)::BIGINT
      AS 日均交易量_月平均,

    MAX(交易總筆數)::BIGINT AS 單日峰值交易量,
    arg_max(tx_date, 交易總筆數) AS 單日峰值日期,

    -- 成功/失敗
    SUM(成功筆數)::BIGINT AS 月成功筆數,
    SUM(失敗筆數)::BIGINT AS 月失敗筆數,
    SUM(逾時筆數)::BIGINT AS 月逾時筆數,

    ROUND(
      SUM(成功筆數) * 100.0 / NULLIF(SUM(交易總筆數), 0),
      2
    ) AS 月成功率百分比
  FROM base
  GROUP BY 年, 年月, 交易類型
)
SELECT * FROM m;
