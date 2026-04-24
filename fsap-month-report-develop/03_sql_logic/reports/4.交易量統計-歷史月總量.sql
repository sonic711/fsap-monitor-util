WITH params AS (
    SELECT
        '2025-09' AS StartYM,   -- 往前推一個月作為基底
        '2026-04' AS EndYM,
        'FAC2FAS' AS ExcludePrId -- 🌟 將排除清單也參數化
), 
Exclude_PR_ID AS (
    -- 🌟 修正：要有 FROM 才能從 params 抓資料，而且要對應別名 p
    SELECT UNNEST(string_split(p.ExcludePrId, ',')) AS PR_ID
    FROM params p
),
MonthlyVolume AS (
    SELECT 
        t.tx_yyyymm AS "年月",
        SUM(t.tx_cnt) AS "月總交易量"
    FROM v_rt_pr_hh24_clean t
    CROSS JOIN params p       -- 🌟 修正 2：必須 CROSS JOIN 才能呼叫參數
    WHERE (1 = 1)
      AND t.tx_yyyymm BETWEEN p.StartYM AND p.EndYM
      AND t.PR_ID NOT IN (SELECT PR_ID FROM Exclude_PR_ID)
    GROUP BY t.tx_yyyymm
),
CalcDiff AS (
    -- 🌟 修正 3：必須先在這裡把 LAG() 算好，才能進行下一步的過濾
    SELECT 
        "年月",
        "月總交易量",
        ROUND(("月總交易量" - LAG("月總交易量") OVER (ORDER BY "年月")) * 100.0 / 
        NULLIF(LAG("月總交易量") OVER (ORDER BY "年月"), 0), 2) || '%' AS "與上月比較差異(%)"
    FROM MonthlyVolume
)

-- 最後一步：拿出算好的結果，並把作為「基底」的 9 月份隱藏起來
SELECT 
    c."年月",
    c."月總交易量",
    c."與上月比較差異(%)"
FROM CalcDiff c
CROSS JOIN params p
WHERE c."年月" > p.StartYM   -- 動態過濾掉 StartYM (即 > '2025-09')
ORDER BY c."年月";

/***
SELECT 
    tx_yyyymm AS "年月",
    SUM(total_cnt) AS "月總交易量"
FROM v_rt_cnt_clean

-- 條件 1：指定要統計的月份 (例如 2026 年 1 月)
WHERE (1 = 1)
AND tx_yyyymm BETWEEN '2025-10' AND '2026-02' 

-- 條件 2：排除 FAC2FAS (也就是計算金融服務應用平台 FSAP 的總量)
  AND PR_ID != 'FAC2FAS'

-- 依據年月進行分組加總
GROUP BY tx_yyyymm
ORDER BY 1
;
***/