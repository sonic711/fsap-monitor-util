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
        SUM(t.total_cnt) AS "日總交易量"
    FROM v_rt_cnt_clean t
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
        NULLIF(LAG("單日峰值交易量") OVER (ORDER BY "年月"), 0), 2) || '%' AS "與上月比較差異(%)"
    FROM MonthlyPeak
)

-- 步驟 5：過濾掉用來當作基底的月份 (2025-09)，呈現最終結果
SELECT
    c."年月",
    c."單日峰值日期",
    c."單日峰值交易量",
    c."與上月比較差異(%)"
FROM CalcDiff c
CROSS JOIN params p
WHERE c."年月" > p.StartYM
ORDER BY c."年月" ASC;



/***
WITH DailyTotal AS (
    -- 步驟 1：先算出歷史以來「每一天」的總交易量 (排除 FAC2FAS)
    SELECT
        tx_yyyymm AS "年月",
        tx_dt_str AS "交易日",
        SUM(total_cnt) AS "日總交易量"
    FROM v_rt_cnt_clean
    WHERE PR_ID != 'FAC2FAS' -- 如果想看全系統總計，請把這行註解掉
AND tx_yyyymm BETWEEN '2025-10' AND '2026-03'
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
