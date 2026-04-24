CREATE OR REPLACE VIEW v_pr_info AS
SELECT 
    CAST("順序" AS INTEGER) AS seq_no,
    "交易類型" AS PR_ID,
    "電文分類" AS PR_CATEGORY,
    "交易名稱" AS PR_NAME
FROM read_csv_auto('00_info/PR_INFO.csv');
