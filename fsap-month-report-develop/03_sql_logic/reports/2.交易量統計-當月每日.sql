WITH params AS (
    -- 🌟 [參數控制台] 修改此處即可產出不同月份的報表
    SELECT 
        '2026-04' AS target_month,   -- 指定統計年月
        'FAC2FAS' AS exclude_pr_id    -- 指定要排除的交易代碼
)
SELECT 
    -- 使用 RIGHT 函數，只抓取 '2026-03-01' 的最後五個字變成 '03-01'
    RIGHT(tx_dt_str, 5) AS "交易日期",
    
    SUM(tx_cnt) AS "每日總交易量"
FROM v_rt_pr_hh24_clean
CROSS JOIN params p  -- 🔗 關聯參數

-- 條件 1：限定月份
WHERE tx_yyyymm = p.target_month 

-- 條件 2：排除特定交易類型
  AND PR_ID != p.exclude_pr_id

-- 這裡保持用完整的日期來分組和排序，確保不會亂掉
GROUP BY 1
ORDER BY 1;
