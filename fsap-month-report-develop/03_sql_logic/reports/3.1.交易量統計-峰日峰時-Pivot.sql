WITH params AS (
    -- 🌟 [參數控制台] 修改此處即可產出不同月份的報表
    SELECT 
        '${targetMonth}' AS target_month,   -- 指定統計年月
        'FAC2FAS' AS exclude_pr_id    -- 指定要排除的交易代碼
),
-- 找出指定月份中交易量最大的那一天 (峰日)
peak_day_select AS (
    SELECT tx_dt_str
    FROM v_rt_pr_hh24_clean
    CROSS JOIN params p
    WHERE tx_yyyymm = p.target_month 
      AND PR_ID != p.exclude_pr_id
    GROUP BY tx_dt_str
    ORDER BY SUM(tx_cnt) DESC
    LIMIT 1
)
PIVOT (
    -- 步驟 1：撈出需要的原始明細資料
    SELECT 
        -- 把數字小時轉換成兩位數文字，例如 9 變成 '09'，當作未來的欄位名稱
        LPAD(CAST(tx_hour AS VARCHAR), 2, '0') || ':00' AS "小時",
        tx_cnt
    FROM v_rt_pr_hh24_clean
    CROSS JOIN params p
    WHERE tx_dt_str = (SELECT tx_dt_str FROM peak_day_select)
      AND PR_ID != p.exclude_pr_id
) 
-- 步驟 2：指定把「小時」這個欄位攤平成橫向的欄位
ON "小時" 

-- 步驟 3：指定裡面的數值要用加總 (SUM) 來計算
USING SUM(tx_cnt);
