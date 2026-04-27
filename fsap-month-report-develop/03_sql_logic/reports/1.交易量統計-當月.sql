WITH Config AS (
    -- 🌟 步驟 0：【參數控制台】未來換月或換系統，只要改這裡的 3 行就好！
    SELECT 
        '${targetMonth}' AS target_month,       -- 指定要查詢的年月
        'FAC2FAS' AS exclude_pr_id,             -- 指定要排除的交易代碼
        '金融服務應用平台 FSAP' AS report_sys_name -- 報表上要顯示的系統名稱
),
FSAP_Daily AS (
    -- 步驟 1：算出每天的全系統加總交易量
    SELECT 
        d.tx_dt_str AS "交易日",
        SUM(d.tx_cnt) AS "日交易量"
    FROM v_rt_pr_hh24_clean d
    CROSS JOIN Config c  -- 🔗 呼叫參數
    WHERE d.tx_yyyymm = c.target_month 
      AND d.PR_ID != c.exclude_pr_id
    GROUP BY d.tx_dt_str
),
FSAP_PeakDay AS (
    -- 步驟 2：算出「整月總量」，並抓出「最高高峰日」 (只取第1名)
    SELECT 
        (SELECT SUM("日交易量") FROM FSAP_Daily) AS "月交易總量",
        "交易日" AS "峰日日期",
        "日交易量" AS "峰日交易量"
    FROM FSAP_Daily
    ORDER BY "日交易量" DESC
    LIMIT 1
),
FSAP_PeakHour AS (
    -- 步驟 3：拿峰日去每小時的表找「峰時」
    SELECT 
        h.tx_hour AS "峰時",
        SUM(h.tx_cnt) AS "峰時交易量"
    FROM v_rt_pr_hh24_clean h
    JOIN FSAP_PeakDay p ON h.tx_dt_str = p."峰日日期"
    CROSS JOIN Config c  -- 🔗 再次呼叫參數
    WHERE h.PR_ID != c.exclude_pr_id
    GROUP BY h.tx_hour
    ORDER BY SUM(h.tx_cnt) DESC
    LIMIT 1
)

-- 步驟 4：組合最終結果，直接對應您的簡報表格
SELECT 
    c.report_sys_name AS "系統",  -- 🔗 從參數台抓取系統名稱
    p."月交易總量",
    p."峰日日期",
    p."峰日交易量",
    -- 格式化時間區間 (例如 14 -> 14:00~14:59)
    LPAD(CAST(h."峰時" AS VARCHAR), 2, '0') || ':00~' || 
    LPAD(CAST(h."峰時" AS VARCHAR), 2, '0') || ':59' AS "峰日峰時區間",
    h."峰時交易量"
FROM FSAP_PeakDay p
CROSS JOIN FSAP_PeakHour h
CROSS JOIN Config c;  -- 🔗 呼叫參數供 SELECT 使用
