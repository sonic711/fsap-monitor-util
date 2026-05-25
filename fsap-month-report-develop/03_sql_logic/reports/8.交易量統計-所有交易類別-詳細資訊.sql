-- =============================================
-- 報表名稱：交易量統計 - 所有交易類別 - 詳細資訊（View 版）
-- 底層視圖：v_monthly_transaction_stats
-- 適用情境：標準整月統計，不需排除特定 PR_ID
-- 若需排除 PR_ID 或非整月區間，請改用：8.交易量統計-所有交易類別-詳細資訊.sql
-- =============================================

WITH params AS (
    SELECT
        '${targetMonth}' AS TargetMonth,
        '' AS TargetCategory -- 'update' / 'query' / ''(全部)
)

SELECT
    "交易類型",
    "類別",
    "名稱",
    "交易總量",
    "峰日",
    "峰日交易量",
    "峰時區間",
    "峰時交易量",
    "平均處理時間",
    "峰值處理時間",
    "交易量佔比",
    "交易開始日期",
    "交易結束日期",
    "實際交易天數"

FROM v_monthly_transaction_stats
CROSS JOIN params p
WHERE "年月" = p.TargetMonth
  AND ("類別" = p.TargetCategory OR p.TargetCategory = '')
ORDER BY "類別" DESC, "交易總量" DESC;
