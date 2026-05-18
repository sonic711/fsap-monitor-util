WITH params AS (
    SELECT
        '${historyStartMonth}' AS StartYM,    -- 往前推一個月作為基底
        '${historyEndMonth}' AS EndYM,
        'FAC2FAS' AS ExcludePrId -- 排除清單
), 
Exclude_PR_ID AS (
    -- 讀取參數並將字串轉換為排除清單
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
), 
MonthlyVolume AS (
    -- 步驟 1：計算每個月的總交易量
    SELECT 
        t.tx_yyyymm AS "年月",
        SUM(t.tx_cnt) AS "月總交易量"
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p
    WHERE (1 = 1)
      AND t.tx_yyyymm BETWEEN p.StartYM AND p.EndYM
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.tx_yyyymm
),
CalcDiff AS (
    -- 步驟 2：利用 LAG() 算出「差額」與「差異百分比」
    SELECT 
        "年月",
        "月總交易量",
        -- 差額 (本月 - 上月)
        ("月總交易量" - LAG("月總交易量") OVER (ORDER BY "年月")) AS "與上月比較差異",
        -- 差異百分比
        ROUND(("月總交易量" - LAG("月總交易量") OVER (ORDER BY "年月")) * 100.0 / 
        NULLIF(LAG("月總交易量") OVER (ORDER BY "年月"), 0), 2) AS "差異百分比"
    FROM MonthlyVolume
),
FilteredDiff AS (
    -- 步驟 3：過濾掉沒有上月數據的基底月份
    SELECT c.*
    FROM CalcDiff c
    CROSS JOIN params p
    WHERE c."年月" > p.StartYM
),
UnpivotData AS (
    -- 步驟 4：將三個指標拆成三列，並轉為字串方便後續 PIVOT
    -- 💡 注意：第三列的項目名稱刻意給 '3. ' (空白)，符合您簡報上不顯示名稱的需求
    SELECT '1.總交易量' AS "項目", "年月", CAST("月總交易量" AS VARCHAR) AS "數值" FROM FilteredDiff
    UNION ALL
    SELECT '2.與上月比較' AS "項目", "年月", CAST("與上月比較差異" AS VARCHAR) AS "數值" FROM FilteredDiff
    UNION ALL
    SELECT '3. ' AS "項目", "年月", CAST("差異百分比" AS VARCHAR) || '%' AS "數值" FROM FilteredDiff
)

-- 步驟 5：執行 PIVOT 將「年月」攤平成橫向欄位
PIVOT UnpivotData
ON "年月"
USING FIRST("數值")
ORDER BY "項目";
