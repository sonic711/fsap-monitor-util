WITH params AS (
    -- 🌟 升級版控制台：支援精確到分鐘的任意區間
    SELECT 
        '2026-04-01 00:00' AS StartTime,
        '2026-04-30 23:59' AS EndTime,
        '' AS ExcludePrId,
        ''  AS TargetCategory -- 'update' 為交易類，'query' 為查詢類, '' 為全部
),
Exclude_PR_ID AS (
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
),
BaseDaily AS (
    -- 步驟 1：每日彙整
    SELECT 
        t.PR_ID,
        t.tx_dt_str,
        SUM(t.tx_cnt) AS daily_cnt,
        SUM(t.avg_tm_ms * t.tx_cnt) / NULLIF(SUM(t.tx_cnt), 0) AS daily_avg_ms
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime, 1, 10) AND SUBSTRING(p.EndTime, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime AND p.EndTime
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID, t.tx_dt_str
),
MonthlyTotal AS (
    -- 步驟 2：全系統總交易量 (佔比的分母)
    SELECT SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily
),
SystemStats AS (
    -- 步驟 3：只針對目標分類計算區間數據
    SELECT 
        b.PR_ID,
        i.PR_CATEGORY,
        SUM(b.daily_cnt) AS sys_total_cnt,
        SUM(b.daily_avg_ms * b.daily_cnt) / NULLIF(SUM(b.daily_cnt), 0) AS sys_avg_ms,
        MIN(b.tx_dt_str) AS first_tx_date,
        MAX(b.tx_dt_str) AS last_tx_date,
        
        -- 🌟 [新增] 計算實際有發生交易的天數
        COUNT(b.tx_dt_str) AS tx_days_cnt
        
    FROM BaseDaily b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    CROSS JOIN params p
    WHERE (i.PR_CATEGORY = p.TargetCategory OR p.TargetCategory = '') 
    GROUP BY b.PR_ID, i.PR_CATEGORY
),
PeakDayStats AS (
    -- 步驟 4：找出每個 PR_ID 的峰日與峰值時間
    SELECT 
        PR_ID,
        tx_dt_str AS peak_dt,
        daily_cnt AS peak_day_cnt,
        daily_avg_ms AS peak_avg_ms,
        ROW_NUMBER() OVER(PARTITION BY PR_ID ORDER BY daily_cnt DESC) as rn
    FROM BaseDaily
),
PeakHourStats AS (
    -- 步驟 4.5：找出峰時與發生時間
    SELECT 
        t.PR_ID,
        MAX(t.tx_cnt) AS peak_hour_cnt,
        arg_max(t.tx_dt_str, t.tx_cnt) AS peak_hour_dt, 
        arg_max(t.tx_hour, t.tx_cnt) AS peak_hour_hr    
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime, 1, 10) AND SUBSTRING(p.EndTime, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime AND p.EndTime
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID
)

-- 步驟 5：組合所有數據
SELECT 
    m.PR_ID AS "交易類型",
    m.PR_CATEGORY AS "類別",
    COALESCE(i.PR_NAME, '未定義名稱') AS "名稱", 
    m.sys_total_cnt AS "交易總量",
    
    -- 峰日資訊
    CAST(CAST(SUBSTRING(p.peak_dt, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(p.peak_dt, 6, 2) || '/' || 
    SUBSTRING(p.peak_dt, 9, 2) AS "峰日",
    p.peak_day_cnt AS "峰日交易量",
    
    -- 峰時資訊
    CAST(CAST(SUBSTRING(ph.peak_hour_dt, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(ph.peak_hour_dt, 6, 2) || '/' || 
    SUBSTRING(ph.peak_hour_dt, 9, 2) || ' ' ||
    LPAD(CAST(ph.peak_hour_hr AS VARCHAR), 2, '0') || ':00~' || 
    LPAD(CAST(ph.peak_hour_hr AS VARCHAR), 2, '0') || ':59' AS "峰時區間",
    ph.peak_hour_cnt AS "峰時交易量",
    
    CAST(ROUND(m.sys_avg_ms, 0) AS INT) AS "平均處理時間",
    
    -- 峰值處理時間
    CONCAT(
        CAST(CAST(SUBSTRING(p.peak_dt, 1, 4) AS INT) - 1911 AS VARCHAR), '/',
        SUBSTRING(p.peak_dt, 6, 2), '/', 
        SUBSTRING(p.peak_dt, 9, 2), 
        CHR(10), 
        CAST(ROUND(p.peak_avg_ms, 0) AS INT)
    ) AS "峰值處理時間",
    
    -- 自動計算佔比
    CONCAT(CAST(ROUND((m.sys_total_cnt * 100.0) / t.all_sys_total_cnt, 2) AS VARCHAR), '%') AS "交易量佔比",

    -- 交易開始與結束時間
    CAST(CAST(SUBSTRING(m.first_tx_date, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(m.first_tx_date, 6, 2) || '/' || 
    SUBSTRING(m.first_tx_date, 9, 2) AS "交易開始日期",
    
    CAST(CAST(SUBSTRING(m.last_tx_date, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(m.last_tx_date, 6, 2) || '/' || 
    SUBSTRING(m.last_tx_date, 9, 2) AS "交易結束日期",
    
    -- 🌟 [新增] 呈現實際交易天數
    m.tx_days_cnt AS "實際交易天數"

FROM SystemStats m
JOIN PeakDayStats p ON m.PR_ID = p.PR_ID AND p.rn = 1
JOIN PeakHourStats ph ON m.PR_ID = ph.PR_ID
JOIN v_pr_info i ON m.PR_ID = i.PR_ID
CROSS JOIN MonthlyTotal t
ORDER BY m.PR_CATEGORY DESC, m.sys_total_cnt DESC;