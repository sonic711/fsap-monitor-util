WITH params AS (
    -- 🌟 [參數控制台] 修改此處即可產出不同月份的報表
    SELECT
        '${targetMonth}' AS target_month,   -- 指定統計年月
        'FAC2FAS' AS exclude_pr_id    -- 指定要排除的交易代碼
),
DailySummary AS (
    SELECT
        RIGHT(tx_dt_str, 5) AS "交易日期",
        SUM(total_cnt) AS daily_total_cnt
    FROM v_rt_cnt_clean
    CROSS JOIN params p  -- 🔗 關聯參數
    WHERE tx_yyyymm = p.target_month
      AND PR_ID != p.exclude_pr_id
    GROUP BY 1
    ORDER BY 1
)
PIVOT DailySummary
ON "交易日期"
USING SUM(daily_total_cnt);
