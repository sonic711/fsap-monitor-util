-- =============================================
-- MOM 極簡比較查詢
-- 欄位：前一個月年月 / 這個月年月 / 交易類型 / 類別 / 名稱 + 拆分筆數佔比
-- =============================================

WITH params AS (
    SELECT 
        '${previousTargetMonth}' AS prev_month,
        '${targetMonth}' AS curr_month
),

PrevMonth AS (
    SELECT 
        "交易類型",
        "類別",
        "名稱",
        "交易總量",
        "交易量佔比"
    FROM v_monthly_transaction_stats
    WHERE 年月 = (SELECT prev_month FROM params)
),

CurrMonth AS (
    SELECT 
        "交易類型",
        "交易總量",
        "交易量佔比"
    FROM v_monthly_transaction_stats
    WHERE 年月 = (SELECT curr_month FROM params)
)

SELECT
    ROW_NUMBER() OVER (ORDER BY c."交易總量" DESC) AS "排名",
    
    (SELECT prev_month FROM params) AS "前一個月年月",
    (SELECT curr_month FROM params) AS "這個月年月",
    
    p."交易類型" AS "交易類型",
    p."類別" AS "類別",
    p."名稱" AS "名稱",
    
    -- 前一個月（已拆欄）
    p."交易總量" AS "前一個月筆數",
    p."交易量佔比" AS "前一個月佔比",
    
    -- 這個月（已拆欄）
    c."交易總量" AS "這個月筆數",
    c."交易量佔比" AS "這個月佔比",
    
    -- MOM 比較
    (c."交易總量" - p."交易總量") AS "筆數變化",
    ROUND(
        100.0 * (c."交易總量" - p."交易總量") / NULLIF(p."交易總量", 0), 
        2
    ) AS "變化率_%"

FROM PrevMonth p
JOIN CurrMonth c ON p."交易類型" = c."交易類型"
ORDER BY c."交易總量" DESC;
