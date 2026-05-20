-- mom_transaction_detail
WITH params AS (
    -- 🌟 MOM 控制台：一次設定兩個月份（可自行調整日期）
    SELECT
        '${previousRangeStartTime}' AS StartTime_Mar,
        '${previousRangeEndTime}' AS EndTime_Mar,
        '${rangeStartTime}' AS StartTime_Apr,
        '${rangeEndTime}' AS EndTime_Apr,
        '' AS ExcludePrId,           -- 要排除的 PR_ID（逗號分隔）
        '' AS TargetCategory         -- 'update' / 'query' / ''(全部)
),

Exclude_PR_ID AS (
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
),

-- ====================== 前一月數據 ======================
BaseDaily_Mar AS (
    SELECT
        t.PR_ID,
        t.tx_dt_str,
        SUM(t.tx_cnt) AS daily_cnt,
        SUM(t.avg_tm_ms * t.tx_cnt) / NULLIF(SUM(t.tx_cnt), 0) AS daily_avg_ms
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime_Mar, 1, 10) AND SUBSTRING(p.EndTime_Mar, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime_Mar AND p.EndTime_Mar
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID, t.tx_dt_str
),

MonthlyTotal_Mar AS (
    SELECT SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily_Mar
),

SystemStats_Mar AS (
    SELECT
        b.PR_ID,
        i.PR_CATEGORY,
        SUM(b.daily_cnt) AS sys_total_cnt_Mar,
        SUM(b.daily_avg_ms * b.daily_cnt) / NULLIF(SUM(b.daily_cnt), 0) AS sys_avg_ms_Mar,
        COUNT(b.tx_dt_str) AS tx_days_cnt_Mar,
        MIN(b.tx_dt_str) AS first_tx_date_Mar,
        MAX(b.tx_dt_str) AS last_tx_date_Mar
    FROM BaseDaily_Mar b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    CROSS JOIN params p
    WHERE (i.PR_CATEGORY = p.TargetCategory OR p.TargetCategory = '')
    GROUP BY b.PR_ID, i.PR_CATEGORY
),

PeakDay_Mar AS (
    SELECT
        PR_ID,
        tx_dt_str AS peak_dt_Mar,
        daily_cnt AS peak_day_cnt_Mar,
        daily_avg_ms AS peak_avg_ms_Mar,
        ROW_NUMBER() OVER(PARTITION BY PR_ID ORDER BY daily_cnt DESC) AS rn
    FROM BaseDaily_Mar
),

PeakHour_Mar AS (
    SELECT
        t.PR_ID,
        MAX(t.tx_cnt) AS peak_hour_cnt_Mar,
        arg_max(t.tx_dt_str, t.tx_cnt) AS peak_hour_dt_Mar,
        arg_max(t.tx_hour, t.tx_cnt) AS peak_hour_hr_Mar
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime_Mar, 1, 10) AND SUBSTRING(p.EndTime_Mar, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime_Mar AND p.EndTime_Mar
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID
),

-- ====================== 本月數據 ======================
BaseDaily_Apr AS (
    SELECT
        t.PR_ID,
        t.tx_dt_str,
        SUM(t.tx_cnt) AS daily_cnt,
        SUM(t.avg_tm_ms * t.tx_cnt) / NULLIF(SUM(t.tx_cnt), 0) AS daily_avg_ms
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime_Apr, 1, 10) AND SUBSTRING(p.EndTime_Apr, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime_Apr AND p.EndTime_Apr
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID, t.tx_dt_str
),

MonthlyTotal_Apr AS (
    SELECT SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily_Apr
),

SystemStats_Apr AS (
    SELECT
        b.PR_ID,
        i.PR_CATEGORY,
        SUM(b.daily_cnt) AS sys_total_cnt_Apr,
        SUM(b.daily_avg_ms * b.daily_cnt) / NULLIF(SUM(b.daily_cnt), 0) AS sys_avg_ms_Apr,
        COUNT(b.tx_dt_str) AS tx_days_cnt_Apr,
        MIN(b.tx_dt_str) AS first_tx_date_Apr,
        MAX(b.tx_dt_str) AS last_tx_date_Apr
    FROM BaseDaily_Apr b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    CROSS JOIN params p
    WHERE (i.PR_CATEGORY = p.TargetCategory OR p.TargetCategory = '')
    GROUP BY b.PR_ID, i.PR_CATEGORY
),

PeakDay_Apr AS (
    SELECT
        PR_ID,
        tx_dt_str AS peak_dt_Apr,
        daily_cnt AS peak_day_cnt_Apr,
        daily_avg_ms AS peak_avg_ms_Apr,
        ROW_NUMBER() OVER(PARTITION BY PR_ID ORDER BY daily_cnt DESC) AS rn
    FROM BaseDaily_Apr
),

PeakHour_Apr AS (
    SELECT
        t.PR_ID,
        MAX(t.tx_cnt) AS peak_hour_cnt_Apr,
        arg_max(t.tx_dt_str, t.tx_cnt) AS peak_hour_dt_Apr,
        arg_max(t.tx_hour, t.tx_cnt) AS peak_hour_hr_Apr
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime_Apr, 1, 10) AND SUBSTRING(p.EndTime_Apr, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime_Apr AND p.EndTime_Apr
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID
)

-- ====================== 最終 MOM 比較表 ======================
SELECT
    m.PR_ID AS "交易類型",
    m.PR_CATEGORY AS "類別",
    COALESCE(i.PR_NAME, '未定義名稱') AS "名稱",

    -- 前一月數據
    m.sys_total_cnt_Mar AS "交易總量_前一月",
    CONCAT(CAST(ROUND((m.sys_total_cnt_Mar * 100.0) / tm.all_sys_total_cnt, 2) AS VARCHAR), '%') AS "佔比_前一月",
    CAST(CAST(SUBSTRING(pd.peak_dt_Mar, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(pd.peak_dt_Mar, 6, 2) || '/' ||
    SUBSTRING(pd.peak_dt_Mar, 9, 2) AS "峰日_前一月",
    pd.peak_day_cnt_Mar AS "峰日交易量_前一月",
    CAST(CAST(SUBSTRING(ph.peak_hour_dt_Mar, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(ph.peak_hour_dt_Mar, 6, 2) || '/' ||
    SUBSTRING(ph.peak_hour_dt_Mar, 9, 2) || ' ' ||
    LPAD(CAST(ph.peak_hour_hr_Mar AS VARCHAR), 2, '0') || ':00~' ||
    LPAD(CAST(ph.peak_hour_hr_Mar AS VARCHAR), 2, '0') || ':59' AS "峰時區間_前一月",
    ph.peak_hour_cnt_Mar AS "峰時交易量_前一月",
    CAST(ROUND(m.sys_avg_ms_Mar, 0) AS INT) AS "平均處理時間_前一月",
    CONCAT(
        CAST(CAST(SUBSTRING(pd.peak_dt_Mar, 1, 4) AS INT) - 1911 AS VARCHAR), '/',
        SUBSTRING(pd.peak_dt_Mar, 6, 2), '/',
        SUBSTRING(pd.peak_dt_Mar, 9, 2),
        CHR(10),
        CAST(ROUND(pd.peak_avg_ms_Mar, 0) AS INT)
    ) AS "峰值處理時間_前一月",
    m.tx_days_cnt_Mar AS "實際交易天數_前一月",

    -- 本月數據
    a.sys_total_cnt_Apr AS "交易總量_本月",
    CONCAT(CAST(ROUND((a.sys_total_cnt_Apr * 100.0) / ta.all_sys_total_cnt, 2) AS VARCHAR), '%') AS "佔比_本月",
    CAST(CAST(SUBSTRING(pd2.peak_dt_Apr, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(pd2.peak_dt_Apr, 6, 2) || '/' ||
    SUBSTRING(pd2.peak_dt_Apr, 9, 2) AS "峰日_本月",
    pd2.peak_day_cnt_Apr AS "峰日交易量_本月",
    CAST(CAST(SUBSTRING(ph2.peak_hour_dt_Apr, 1, 4) AS INT) - 1911 AS VARCHAR) || '/' ||
    SUBSTRING(ph2.peak_hour_dt_Apr, 6, 2) || '/' ||
    SUBSTRING(ph2.peak_hour_dt_Apr, 9, 2) || ' ' ||
    LPAD(CAST(ph2.peak_hour_hr_Apr AS VARCHAR), 2, '0') || ':00~' ||
    LPAD(CAST(ph2.peak_hour_hr_Apr AS VARCHAR), 2, '0') || ':59' AS "峰時區間_本月",
    ph2.peak_hour_cnt_Apr AS "峰時交易量_本月",
    CAST(ROUND(a.sys_avg_ms_Apr, 0) AS INT) AS "平均處理時間_本月",
    CONCAT(
        CAST(CAST(SUBSTRING(pd2.peak_dt_Apr, 1, 4) AS INT) - 1911 AS VARCHAR), '/',
        SUBSTRING(pd2.peak_dt_Apr, 6, 2), '/',
        SUBSTRING(pd2.peak_dt_Apr, 9, 2),
        CHR(10),
        CAST(ROUND(pd2.peak_avg_ms_Apr, 0) AS INT)
    ) AS "峰值處理時間_本月",
    a.tx_days_cnt_Apr AS "實際交易天數_本月",

    -- MOM 比較（重點！）
    (a.sys_total_cnt_Apr - m.sys_total_cnt_Mar) AS "變化量",
    CASE 
        WHEN m.sys_total_cnt_Mar = 0 THEN NULL
        ELSE ROUND(100.0 * (a.sys_total_cnt_Apr - m.sys_total_cnt_Mar) / m.sys_total_cnt_Mar, 2)
    END AS "變化率_%"

FROM SystemStats_Mar m
JOIN SystemStats_Apr a ON m.PR_ID = a.PR_ID
JOIN PeakDay_Mar pd ON m.PR_ID = pd.PR_ID AND pd.rn = 1
JOIN PeakDay_Apr pd2 ON a.PR_ID = pd2.PR_ID AND pd2.rn = 1
JOIN PeakHour_Mar ph ON m.PR_ID = ph.PR_ID
JOIN PeakHour_Apr ph2 ON a.PR_ID = ph2.PR_ID
JOIN v_pr_info i ON m.PR_ID = i.PR_ID
CROSS JOIN MonthlyTotal_Mar tm
CROSS JOIN MonthlyTotal_Apr ta
ORDER BY m.PR_CATEGORY DESC, m.sys_total_cnt_Mar DESC;
