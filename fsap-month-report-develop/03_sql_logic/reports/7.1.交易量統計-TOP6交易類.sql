WITH params AS (
    SELECT 
        '${targetMonth}' AS TargetMonth,
        'FAC2FAS' AS ExcludePrId,
        'update'  AS TargetCategory -- 🌟 控制台：'update' 為交易類，'query' 為查詢類, '' 為全部
),
Exclude_PR_ID AS (
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
),
BaseDaily AS (
    -- 步驟 1：每日彙整 (包含全系統，用來算總母數)
    SELECT 
        t.PR_ID,
        t.tx_dt_str,
        SUM(t.tx_cnt) AS daily_cnt,
        SUM(t.avg_tm_ms * t.tx_cnt) / NULLIF(SUM(t.tx_cnt), 0) AS daily_avg_ms
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str LIKE p.TargetMonth || '-%'
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID, t.tx_dt_str
),
MonthlyTotal AS (
    -- 步驟 2：全系統總交易量 (佔比的分母，排除 FAC2FAS 但不分交易或查詢)
    SELECT SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily
),
SystemStats AS (
    -- 步驟 3：只針對目標分類 TargetCategory 計算全月數據
    SELECT 
        b.PR_ID,
        SUM(b.daily_cnt) AS sys_total_cnt,
        SUM(b.daily_avg_ms * b.daily_cnt) / NULLIF(SUM(b.daily_cnt), 0) AS sys_avg_ms
    FROM BaseDaily b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    CROSS JOIN params p
    WHERE (i.PR_CATEGORY = p.TargetCategory OR p.TargetCategory = '') -- 🌟 動態過濾分類
    GROUP BY b.PR_ID
),
PeakDayStats AS (
    -- 步驟 4：找出每個 PR_ID 的峰日與峰值時間
    SELECT 
        PR_ID,
        tx_dt_str AS peak_dt,
        daily_avg_ms AS peak_avg_ms,
        ROW_NUMBER() OVER(PARTITION BY PR_ID ORDER BY daily_cnt DESC) as rn
    FROM BaseDaily
)

-- 步驟 5：組合所有數據
SELECT 
    m.PR_ID AS "交易類型",
    COALESCE(i.PR_NAME, '未定義名稱') AS "名稱", -- 🌟 自動從 v_pr_info 帶入中文名稱
    m.sys_total_cnt AS "交易總量",
    CAST(ROUND(m.sys_avg_ms, 0) AS INT) AS "平均處理時間",
    
    -- 動態計算民國年並串接換行與處理時間
    CONCAT(
        CAST(CAST(SUBSTRING(p.peak_dt, 1, 4) AS INT) - 1911 AS VARCHAR), '/',
        SUBSTRING(p.peak_dt, 6, 2), '/', 
        SUBSTRING(p.peak_dt, 9, 2), 
        CHR(10), 
        CAST(ROUND(p.peak_avg_ms, 0) AS INT)
    ) AS "峰值處理時間",
    
    -- 自動計算佔比
    CONCAT(CAST(ROUND((m.sys_total_cnt * 100.0) / t.all_sys_total_cnt, 2) AS VARCHAR), '%') AS "交易量佔比"

FROM SystemStats m
JOIN PeakDayStats p ON m.PR_ID = p.PR_ID AND p.rn = 1
JOIN v_pr_info i ON m.PR_ID = i.PR_ID
CROSS JOIN MonthlyTotal t
ORDER BY m.sys_total_cnt DESC
LIMIT 6
;
