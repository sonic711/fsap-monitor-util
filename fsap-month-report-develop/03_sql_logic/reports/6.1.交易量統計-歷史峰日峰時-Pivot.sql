WITH params AS (
    SELECT
        '${historyStartMonth}' AS StartYM,    -- 🌟 往前推一個月作為基底
        '${historyEndMonth}' AS EndYM,
        'FAC2FAS' AS ExcludePrId -- 排除清單
), 
Exclude_PR_ID AS (
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
),
FSAP_Daily AS (
    -- 步驟 1：算出每個月中「每一天」的總交易量
    SELECT 
        t.tx_yyyymm AS "年月",
        t.tx_dt_str AS "交易日",
        SUM(t.tx_cnt) AS "日總交易量"
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE t.tx_yyyymm BETWEEN p.StartYM AND p.EndYM
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.tx_yyyymm, t.tx_dt_str
),
FSAP_MonthlyPeakDay AS (
    -- 步驟 2：利用 ROW_NUMBER() 找出「每個月」交易量排第 1 名的那一天 (峰日)
    SELECT 
        "年月",
        "交易日",
        "日總交易量",
        ROW_NUMBER() OVER(PARTITION BY "年月" ORDER BY "日總交易量" DESC) as rn
    FROM FSAP_Daily
),
FSAP_PeakDayHourly AS (
    -- 步驟 3：只針對找出來的「峰日」，去算當天「每小時」的總交易量
    SELECT 
        h.tx_dt_str AS "交易日",
        h.tx_hour AS "小時",
        SUM(h.tx_cnt) AS "時交易量"
    FROM v_rt_pr_hh24_clean h
    -- JOIN 步驟 2 的結果，確保我們只撈峰日那天的每小時資料
    JOIN FSAP_MonthlyPeakDay p ON h.tx_dt_str = p."交易日" AND p.rn = 1
    WHERE h.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY h.tx_dt_str, h.tx_hour
),
FSAP_PeakHourRank AS (
    -- 步驟 4：找出峰日當天，交易量最高的「那一個小時」
    SELECT 
        "交易日",
        "小時",
        "時交易量",
        ROW_NUMBER() OVER(PARTITION BY "交易日" ORDER BY "時交易量" DESC) as hrn
    FROM FSAP_PeakDayHourly
),
BaseResult AS (
    -- 步驟 5：將峰日與峰時的資訊組合起來，並將時間格式化
    SELECT 
        p."年月",
        p."交易日" AS "峰日日期",
        LPAD(CAST(h."小時" AS VARCHAR), 2, '0') || ':00~' || 
        LPAD(CAST(h."小時" AS VARCHAR), 2, '0') || ':59' AS "峰時區間",
        p."交易日" || ' ' || 
        LPAD(CAST(h."小時" AS VARCHAR), 2, '0') || ':00~' || 
        LPAD(CAST(h."小時" AS VARCHAR), 2, '0') || ':59' AS "峰日峰時",
        h."時交易量" AS "峰時交易量"
    FROM FSAP_MonthlyPeakDay p
    JOIN FSAP_PeakHourRank h ON p."交易日" = h."交易日"
    WHERE p.rn = 1 AND h.hrn = 1
),
CalcDiff AS (
    -- 步驟 6：利用 LAG() 計算本月峰時與「上個月峰時」的差異百分比
    SELECT 
        "年月",
        "峰日日期",
        "峰時區間",
        "峰日峰時",
        "峰時交易量",
        ROUND(("峰時交易量" - LAG("峰時交易量") OVER (ORDER BY "年月")) * 100.0 / 
        NULLIF(LAG("峰時交易量") OVER (ORDER BY "年月"), 0), 2) || '%' AS "與上月比較差異(%)"
    FROM BaseResult
),
FinalResult AS (
    -- 步驟 7：過濾掉當作基底的月份，並將所有數值強制轉為 VARCHAR (因為 Pivot 只能裝同一種型別)
    SELECT 
        c."年月",
        CAST(c."峰日日期" AS VARCHAR) AS "峰日日期",
        CAST(c."峰時區間" AS VARCHAR) AS "峰時區間",
        CAST(c."峰時交易量" AS VARCHAR) AS "峰時交易量",
        COALESCE(CAST(c."與上月比較差異(%)" AS VARCHAR), '-') AS "與上月比較差異(%)"
    FROM CalcDiff c
    CROSS JOIN params p
    WHERE c."年月" > p.StartYM
),
UnpivotFormat AS (
    -- 🌟 步驟 8：【解直魔法】把 4 個指標拆成上下堆疊的列，並加上隱藏排序 `_sort`
    SELECT 1 AS _sort, '1. 峰日日期' AS "指標", "年月", "峰日日期" AS "數值" FROM FinalResult
    UNION ALL
    SELECT 2 AS _sort, '2. 峰時區間' AS "指標", "年月", "峰時區間" AS "數值" FROM FinalResult
    UNION ALL
    SELECT 3 AS _sort, '3. 峰時交易量' AS "指標", "年月", "峰時交易量" AS "數值" FROM FinalResult
    UNION ALL
    SELECT 4 AS _sort, '4. 與上月差異(%)' AS "指標", "年月", "與上月比較差異(%)" AS "數值" FROM FinalResult
),
PivotedResult AS (
    -- 🌟 步驟 9：【轉橫魔法】正式 Pivot
    PIVOT UnpivotFormat
    ON "年月"
    USING FIRST("數值")
    GROUP BY _sort, "指標"
)

-- 步驟 10：完美輸出，並踢掉輔助用的排序欄位
SELECT * EXCLUDE(_sort)
FROM PivotedResult
ORDER BY _sort ASC;
