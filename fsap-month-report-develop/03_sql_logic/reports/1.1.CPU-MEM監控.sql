/***
application	instanceId	app	hostName
FSAP-RUNTIME	FSAPRTTP1:fsap-runtime:10081	FSAP-RUNTIME	10.4.240.183
FSAP-RUNTIME	FSAPRTTP2:fsap-runtime:10081	FSAP-RUNTIME	10.4.240.184
FSAP-RUNTIME	FSAPRTTP3:fsap-runtime:10081	FSAP-RUNTIME	10.4.240.185
FSAP-RUNTIME	FSAPRTTP4:fsap-runtime:10081	FSAP-RUNTIME	10.4.240.186
FSAP-RUNTIME	FSAPRTTY1:fsap-runtime:10081	FSAP-RUNTIME	10.4.240.187
FSAP-RUNTIME	FSAPRTTY2:fsap-runtime:10081	FSAP-RUNTIME	10.4.240.188
***/

WITH params AS (
    -- 定義參數，這裡可以根據需要修改日期範圍
    SELECT 
        '${rangeStartDate}' AS start_date,
        '${rangeEndDate}' AS end_date,
        'FSAP-RUNTIME' AS app_list -- 🌟 如果未來要根據 app_list 來過濾，可以在這裡修改應用程式清單
),
AppList AS (
    -- 🌟 如果未來要根據 app_list 來過濾，可以在這裡把它轉成表格格式，目前先保留這個 CTE 以備未來使用
    SELECT UNNEST(string_split(p.app_list, ',')) AS app_name
    FROM params p
),
SiteMapping AS (
    -- 步驟 1：過濾資料，並根據 IP 貼上「主地」或「異地」的標籤
    SELECT 
        CASE 
            WHEN TARGET_IP IN ('10.4.240.183', '10.4.240.184', '10.4.240.185', '10.4.240.186') THEN '主地'
            WHEN TARGET_IP IN ('10.4.240.187', '10.4.240.188') THEN '異地'
            ELSE '其他'
        END AS site_type,
        avg_cpu_rate,
        avg_mem_rate
    FROM v_prod_monitor_daily_count
    CROSS JOIN params p
    CROSS JOIN AppList a
    WHERE (1 = 1)
    AND application IN (SELECT app_name FROM AppList)  -- 🌟 如果未來要根據 app_list 來過濾，可以在這裡使用
    AND CAST(log_date AS VARCHAR) BETWEEN p.start_date AND p.end_date  -- 動態過濾日期範圍
      -- 鎖定 3 月份的資料
      --AND CAST(log_date AS VARCHAR) LIKE '2026-03-%'
),
SiteMonthlyAvg AS (
    -- 步驟 2：算出主地、異地在整個 2 月份的 CPU 與 Memory 平均值
    SELECT 
        site_type,
        AVG(avg_cpu_rate) AS mth_avg_cpu,
        AVG(avg_mem_rate) AS mth_avg_mem
    FROM SiteMapping
    WHERE site_type IN ('主地', '異地') -- 排除非目標機台
    GROUP BY site_type
)

-- 步驟 3：完美塑形成您簡報上的格式
SELECT 
    'FSAP' AS "專案名稱",
    '使用率(%)' AS "監控標的",
    
    -- 提取主地數據，並四捨五入到小數點第二位
    ROUND(MAX(CASE WHEN site_type = '主地' THEN mth_avg_cpu END), 2) AS "主地_CPU",
    ROUND(MAX(CASE WHEN site_type = '主地' THEN mth_avg_mem END), 2) AS "主地_Memory",
    
    -- 提取異地數據，並四捨五入到小數點第二位
    ROUND(MAX(CASE WHEN site_type = '異地' THEN mth_avg_cpu END), 2) AS "異地_CPU",
    ROUND(MAX(CASE WHEN site_type = '異地' THEN mth_avg_mem END), 2) AS "異地_Memory"

FROM SiteMonthlyAvg;
