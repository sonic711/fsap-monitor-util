WITH params AS (
    -- 🌟 [參數控制台] 修改此處即可產出不同月份的報表
    SELECT
        '${targetMonth}' AS target_month,   -- 指定統計年月
        'FAC2FAS' AS exclude_pr_id    -- 指定要排除的交易代碼
),
-- 找出指定月份中交易量最大的那一天 (峰日)
peak_day_select AS (
    SELECT arg_max(tx_dt_str, day_total) AS tx_dt_str
    FROM (
        SELECT tx_dt_str, SUM(total_cnt) AS day_total
        FROM v_rt_cnt_clean
        CROSS JOIN params p
        WHERE tx_yyyymm = p.target_month
          AND PR_ID != p.exclude_pr_id
        GROUP BY tx_dt_str
    )
)
SELECT
    LPAD(CAST(tx_hour AS VARCHAR), 2, '0') || ':00' AS "小時",
    SUM(tx_cnt) AS "交易量"
FROM v_rt_pr_hh24_clean
CROSS JOIN params p

-- 條件：動態選取上面算出來的「交易量最大日期」
WHERE tx_dt_str = (SELECT tx_dt_str FROM peak_day_select)

-- 條件 2：排除特定交易類型
  AND PR_ID != p.exclude_pr_id

-- 依照「小時」分組加總
GROUP BY 1

-- 依照 0 點到 23 點的時間順序排列，方便直接畫圖
ORDER BY 1 ASC;
