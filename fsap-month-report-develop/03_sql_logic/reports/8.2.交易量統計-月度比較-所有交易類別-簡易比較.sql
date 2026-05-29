-- mom_transaction_summary
WITH params AS (
    --  控制台：調整兩個月份日期即可
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
        SUM(t.tx_cnt) AS daily_cnt
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime_Mar, 1, 10) AND SUBSTRING(p.EndTime_Mar, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime_Mar AND p.EndTime_Mar
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID
),

MonthlyTotal_Mar AS (
    SELECT SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily_Mar
),

SystemStats_Mar AS (
    SELECT
        b.PR_ID,
        i.PR_CATEGORY,
        SUM(b.daily_cnt) AS sys_total_cnt_Mar
    FROM BaseDaily_Mar b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    CROSS JOIN params p
    WHERE (i.PR_CATEGORY = p.TargetCategory OR p.TargetCategory = '')
    GROUP BY b.PR_ID, i.PR_CATEGORY
),

-- ====================== 本月數據 ======================
BaseDaily_Apr AS (
    SELECT
        t.PR_ID,
        SUM(t.tx_cnt) AS daily_cnt
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_dt_str BETWEEN SUBSTRING(p.StartTime_Apr, 1, 10) AND SUBSTRING(p.EndTime_Apr, 1, 10)
      AND t.tx_dt_str || ' ' || LPAD(CAST(t.tx_hour AS VARCHAR), 2, '0') || ':00' BETWEEN p.StartTime_Apr AND p.EndTime_Apr
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.PR_ID
),

MonthlyTotal_Apr AS (
    SELECT SUM(daily_cnt) AS all_sys_total_cnt
    FROM BaseDaily_Apr
),

SystemStats_Apr AS (
    SELECT
        b.PR_ID,
        SUM(b.daily_cnt) AS sys_total_cnt_Apr
    FROM BaseDaily_Apr b
    JOIN v_pr_info i ON b.PR_ID = i.PR_ID
    CROSS JOIN params p
    WHERE (i.PR_CATEGORY = p.TargetCategory OR p.TargetCategory = '')
    GROUP BY b.PR_ID
)

-- ====================== 最終極簡 MOM 報表 ======================
SELECT
    ROW_NUMBER() OVER (ORDER BY a.sys_total_cnt_Apr DESC) AS "排名",
    COALESCE(i.PR_NAME, '未定義名稱') AS "交易名稱",
    
    -- 前一個月
    CONCAT(
        CAST(m.sys_total_cnt_Mar AS VARCHAR),
        ' (',
        ROUND((m.sys_total_cnt_Mar * 100.0) / tm.all_sys_total_cnt, 2),
        '%)'
    ) AS "前一個月筆數（佔比）",
    
    -- 這個月
    CONCAT(
        CAST(a.sys_total_cnt_Apr AS VARCHAR),
        ' (',
        ROUND((a.sys_total_cnt_Apr * 100.0) / ta.all_sys_total_cnt, 2),
        '%)'
    ) AS "這個月筆數（佔比）",
    
    (a.sys_total_cnt_Apr - m.sys_total_cnt_Mar) AS "筆數變化",
    
    CASE 
        WHEN m.sys_total_cnt_Mar = 0 THEN NULL
        ELSE ROUND(100.0 * (a.sys_total_cnt_Apr - m.sys_total_cnt_Mar) / m.sys_total_cnt_Mar, 2)
    END AS "變化率_%"

FROM SystemStats_Mar m
JOIN SystemStats_Apr a ON m.PR_ID = a.PR_ID
JOIN v_pr_info i ON m.PR_ID = i.PR_ID
CROSS JOIN MonthlyTotal_Mar tm
CROSS JOIN MonthlyTotal_Apr ta
ORDER BY a.sys_total_cnt_Apr DESC;
