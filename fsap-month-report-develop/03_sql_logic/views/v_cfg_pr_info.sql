CREATE OR REPLACE VIEW v_cfg_pr_info AS
SELECT *
FROM read_csv_auto('00_info/CFG_PR_INFO.csv');
