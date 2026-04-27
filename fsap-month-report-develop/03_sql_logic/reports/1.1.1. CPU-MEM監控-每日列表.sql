WITH params AS (
    -- 定義參數，這裡可以根據需要修改日期範圍
    SELECT 
        '${rangeStartDate}' AS start_date,
        '${rangeEndDate}' AS end_date,
        'FSAP-RUNTIME' AS app_list
),
AppList AS (
    -- 將 app_list 轉成多列
    SELECT UNNEST(string_split(p.app_list, ',')) AS app_name
    FROM params p
),
SiteMapping AS (
    SELECT 
        log_date,
        CASE 
            WHEN TARGET_IP IN ('10.4.240.183', '10.4.240.184', '10.4.240.185', '10.4.240.186') THEN '主地'
            WHEN TARGET_IP IN ('10.4.240.187', '10.4.240.188') THEN '異地'
            ELSE '其他'
        END AS site_type,
        avg_cpu_rate,
        avg_mem_rate,
        max_cpu_rate,
        max_mem_rate,
        min_cpu_rate,
        min_mem_rate
    -- 🌟 修正 1：用單引號包住整段查詢，裡面的路徑單引號改用 '' 跳脫
    FROM v_prod_monitor_daily_count
    -- 這裡保留 params p 的 CROSS JOIN (因為它永遠只有 1 列)，這是很棒的參數帶入法
    CROSS JOIN params p
    -- 🌟 修正 2：拿掉 CROSS JOIN AppList a，避免資料翻倍，直接在 WHERE 過濾即可
    WHERE (1 = 1)
      AND application IN (SELECT app_name FROM AppList)
      AND CAST(log_date AS VARCHAR) BETWEEN p.start_date AND p.end_date
),
DailyAvg AS (
    -- 算出主地、異地「每一天」的 CPU 與 Memory 平均值
    SELECT 
        log_date,
        site_type,
        AVG(avg_cpu_rate) AS dly_avg_cpu,
        AVG(avg_mem_rate) AS dly_avg_mem,
        AVG(max_cpu_rate) AS dly_max_cpu,
        AVG(max_mem_rate) AS dly_max_mem,
        AVG(min_cpu_rate) AS dly_min_cpu,
        AVG(min_mem_rate) AS dly_min_mem
    FROM SiteMapping
    WHERE site_type IN ('主地', '異地')
    GROUP BY log_date, site_type
)

-- 步驟 3：以「日期」為群組，把主地和異地的指標轉成橫向欄位 (PIVOT 的概念)
SELECT 
    CAST(log_date AS VARCHAR) AS "日期",
    
    ROUND(MAX(CASE WHEN site_type = '主地' THEN dly_avg_cpu END), 2) AS "主地_CPU",
    ROUND(MAX(CASE WHEN site_type = '主地' THEN dly_avg_mem END), 2) AS "主地_Memory",
    
    ROUND(MAX(CASE WHEN site_type = '異地' THEN dly_avg_cpu END), 2) AS "異地_CPU",
    ROUND(MAX(CASE WHEN site_type = '異地' THEN dly_avg_mem END), 2) AS "異地_Memory",

    ROUND(MAX(CASE WHEN site_type = '主地' THEN dly_max_cpu END), 2) AS "主地_CPU_max",
    ROUND(MAX(CASE WHEN site_type = '主地' THEN dly_max_mem END), 2) AS "主地_Memory_max",

    ROUND(MAX(CASE WHEN site_type = '異地' THEN dly_max_cpu END), 2) AS "異地_CPU_min",
    ROUND(MAX(CASE WHEN site_type = '異地' THEN dly_min_mem END), 2) AS "異地_Memory_min"

FROM DailyAvg
GROUP BY log_date
ORDER BY log_date ASC;
