-- =============================================
-- View 名稱：v_monthly_transaction_stats
-- 用途：依年月查詢完整交易統計分析報表
-- 使用方式：SELECT * FROM v_monthly_transaction_stats WHERE year_month = '2026-04'
-- =============================================

CREATE OR REPLACE VIEW v_monthly_transaction_stats AS
WITH BaseDaily AS (
    -- 每日彙整（跨所有月份）
    SELECT 
        SUBSTRING(t.tx_dt_str, 1, 7) AS year_month,   -- ← 關鍵：年月分組
        t.PR_ID,
        t.tx_dt_str,
        SUM(t.tx_cnt) AS daily_cnt,
        SUM(t.avg_tm_ms * t.tx_cnt) / NULLIF(SUM(t.tx_cnt), 0) AS daily_avg_ms
    FROM v_rt_pr_hh24_clean t
    GROUP BY year_month, t.PR_ID, t.tx_dt_str
),

MonthlyTotal AS (
    -- 每月全系統總交易量（佔比分母）
    SELECT 
        year_month,
        SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily
    GROUP BY year_month
),

SystemStats AS (
    -- 每月各 PR 統計
    SELECT 
        b.year_month,
        b.PR_ID,
        i.PR_CATEGORY,
        SUM(b.daily_cnt) AS sys_total_cnt,
        SUM(b.daily_avg_ms * b.daily_cnt) / NULLIF(SUM(b.daily_cnt), 0) AS sys_avg_ms,
        MIN(b.tx_dt_str) AS first_tx_date,
        MAX(b.tx_dt_str) AS last_tx_date,
        COUNT(b.tx_dt_str) AS tx_days_cnt
    FROM BaseDaily b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    GROUP BY b.year_month, b.PR_ID, i.PR_CATEGORY
),

PeakDayStats AS (
    -- 每月處理時間峰日，定義需與第 8 頁原始 SQL 一致。
    SELECT 
        year_month,
        PR_ID,
        tx_dt_str AS peak_dt,
        daily_cnt AS peak_day_cnt,
        daily_avg_ms AS peak_avg_ms,
        ROW_NUMBER() OVER (PARTITION BY year_month, PR_ID ORDER BY daily_avg_ms DESC) as rn
    FROM BaseDaily
),

PeakHourStats AS (
    -- 每月峰時；同交易量時取最早發生時間，避免 arg_max tie 不穩定。
    SELECT 
        year_month,
        PR_ID,
        tx_cnt AS peak_hour_cnt,
        tx_dt_str AS peak_hour_dt,
        tx_hour AS peak_hour_hr
    FROM (
        SELECT
            SUBSTRING(t.tx_dt_str, 1, 7) AS year_month,
            t.PR_ID,
            t.tx_dt_str,
            t.tx_hour,
            t.tx_cnt,
            ROW_NUMBER() OVER (
                PARTITION BY SUBSTRING(t.tx_dt_str, 1, 7), t.PR_ID
                ORDER BY t.tx_cnt DESC, t.tx_dt_str ASC, t.tx_hour ASC
            ) AS rn
        FROM v_rt_pr_hh24_clean t
    ) ranked
    WHERE rn = 1
)

-- 最終輸出（格式與您原本單月報表完全一致）
SELECT 
    s.year_month AS "年月",                     -- ← 新增：可作為查詢條件
    s.PR_ID AS "交易類型",
    s.PR_CATEGORY AS "類別",
    COALESCE(i.PR_NAME, '未定義名稱') AS "名稱", 
    s.sys_total_cnt AS "交易總量",
    
    -- 峰日
    CAST(CAST(SUBSTRING(p.peak_dt, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(p.peak_dt, 6, 2) || '/' || 
    SUBSTRING(p.peak_dt, 9, 2) AS "峰日",
    p.peak_day_cnt AS "峰日交易量",
    
    -- 峰時
    CAST(CAST(SUBSTRING(ph.peak_hour_dt, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(ph.peak_hour_dt, 6, 2) || '/' || 
    SUBSTRING(ph.peak_hour_dt, 9, 2) || ' ' ||
    LPAD(CAST(ph.peak_hour_hr AS VARCHAR), 2, '0') || ':00~' || 
    LPAD(CAST(ph.peak_hour_hr AS VARCHAR), 2, '0') || ':59' AS "峰時區間",
    ph.peak_hour_cnt AS "峰時交易量",
    
    CAST(ROUND(s.sys_avg_ms, 0) AS INT) AS "平均處理時間",
    
    -- 峰值處理時間
    CONCAT(
        CAST(CAST(SUBSTRING(p.peak_dt, 1, 4) AS INT) - 1911 AS VARCHAR), '/',
        SUBSTRING(p.peak_dt, 6, 2), '/', 
        SUBSTRING(p.peak_dt, 9, 2), 
        CHR(10), 
        CAST(ROUND(p.peak_avg_ms, 0) AS INT)
    ) AS "峰值處理時間",
    
    -- 佔比
    CONCAT(CAST(ROUND((s.sys_total_cnt * 100.0) / t.all_sys_total_cnt, 2) AS VARCHAR), '%') AS "交易量佔比",

    -- 交易期間
    CAST(CAST(SUBSTRING(s.first_tx_date, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(s.first_tx_date, 6, 2) || '/' || 
    SUBSTRING(s.first_tx_date, 9, 2) AS "交易開始日期",
    
    CAST(CAST(SUBSTRING(s.last_tx_date, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(s.last_tx_date, 6, 2) || '/' || 
    SUBSTRING(s.last_tx_date, 9, 2) AS "交易結束日期",
    
    s.tx_days_cnt AS "實際交易天數"

FROM SystemStats s
JOIN PeakDayStats p ON s.year_month = p.year_month AND s.PR_ID = p.PR_ID AND p.rn = 1
JOIN PeakHourStats ph ON s.year_month = ph.year_month AND s.PR_ID = ph.PR_ID
JOIN v_pr_info i ON s.PR_ID = i.PR_ID
JOIN MonthlyTotal t ON s.year_month = t.year_month
ORDER BY s.year_month, s.PR_CATEGORY DESC, s.sys_total_cnt DESC;
