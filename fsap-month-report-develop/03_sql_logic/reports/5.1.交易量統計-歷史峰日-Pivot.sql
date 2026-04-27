WITH params AS (
    SELECT
        '${historyStartMonth}' AS StartYM,    -- 往前推一個月作為基底
        '${historyEndMonth}' AS EndYM,
        'FAC2FAS' AS ExcludePrId -- 排除清單
), 
Exclude_PR_ID AS (
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
),
DailyTotal AS (
    -- 步驟 1：算出指定區間內「每一天」的總交易量
    SELECT 
        t.tx_yyyymm AS "年月",
        t.tx_dt_str AS "交易日",
        SUM(t.tx_cnt) AS "日總交易量"
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE (1 = 1)
      AND t.tx_yyyymm BETWEEN p.StartYM AND p.EndYM
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.tx_yyyymm, t.tx_dt_str
),
RankedDailyTotal AS (
    -- 步驟 2：利用 ROW_NUMBER() 找出「每個月」交易量排第 1 名的那一天
    SELECT 
        "年月",
        "交易日",
        "日總交易量",
        ROW_NUMBER() OVER(PARTITION BY "年月" ORDER BY "日總交易量" DESC) as rn
    FROM DailyTotal
),
MonthlyPeak AS (
    -- 步驟 3：只抓取每月排名第 1 的紀錄
    SELECT 
        "年月",
        "交易日" AS "單日峰值日期",
        "日總交易量" AS "單日峰值交易量"
    FROM RankedDailyTotal
    WHERE rn = 1
),
CalcDiff AS (
    -- 步驟 4：利用 LAG() 計算本月峰值與「上個月峰值」的差異百分比
    SELECT 
        "年月",
        "單日峰值日期",
        "單日峰值交易量",
        ROUND(("單日峰值交易量" - LAG("單日峰值交易量") OVER (ORDER BY "年月")) * 100.0 / 
        NULLIF(LAG("單日峰值交易量") OVER (ORDER BY "年月"), 0), 2) AS "差異百分比"
    FROM MonthlyPeak
),
FilteredDiff AS (
    -- 步驟 5：過濾掉用來當作基底的月份
    SELECT c.*
    FROM CalcDiff c
    CROSS JOIN params p
    WHERE c."年月" > p.StartYM
),
UnpivotData AS (
    -- 步驟 6：將三個指標拆成三列，並轉為字串方便後續 PIVOT
    -- 💡 第三列的項目名稱一樣給 '3. ' (空白)，維持版面乾淨
    SELECT '1.單日峰值日期' AS "項目", "年月", CAST("單日峰值日期" AS VARCHAR) AS "數值" FROM FilteredDiff
    UNION ALL
    SELECT '2.單日峰值交易量' AS "項目", "年月", CAST("單日峰值交易量" AS VARCHAR) AS "數值" FROM FilteredDiff
    UNION ALL
    SELECT '3. ' AS "項目", "年月", CAST("差異百分比" AS VARCHAR) || '%' AS "數值" FROM FilteredDiff
)

-- 步驟 7：執行 PIVOT 將「年月」攤平成橫向欄位
PIVOT UnpivotData
ON "年月"
USING FIRST("數值")
ORDER BY "項目";

/***
WITH DailyTotal AS (
    -- 步驟 1：先算出歷史以來「每一天」的總交易量 (排除 FAC2FAS)
    SELECT 
        tx_yyyymm AS "年月",
        tx_dt_str AS "交易日",
        SUM(total_cnt) AS "日總交易量"
    FROM v_rt_cnt_clean
    WHERE PR_ID != 'FAC2FAS' -- 如果想看全系統總計，請把這行註解掉
AND tx_yyyymm BETWEEN '2025-10' AND '2026-02'
    GROUP BY tx_yyyymm, tx_dt_str
),
RankedDailyTotal AS (
    -- 步驟 2：利用 ROW_NUMBER() 找出「每個月」交易量排第 1 名的那一天
    SELECT 
        "年月",
        "交易日",
        "日總交易量",
        ROW_NUMBER() OVER(PARTITION BY "年月" ORDER BY "日總交易量" DESC) as rn
    FROM DailyTotal
)

-- 步驟 3：只抓取每月排名第 1 的紀錄，並依照時間先後排序畫圖
SELECT 
    "年月",
    "交易日" AS "單日峰值日期",
    "日總交易量" AS "單日峰值交易量"
FROM RankedDailyTotal
WHERE rn = 1
ORDER BY "年月" ASC;
***/
