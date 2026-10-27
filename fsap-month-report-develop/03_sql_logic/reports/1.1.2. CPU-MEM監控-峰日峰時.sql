-- 1.1.2 CPU-MEM監控-峰日峰時：月內交易量最大的一天（峰日）與當天交易量最大的小時（峰時），主地／異地的 CPU、Memory 使用率（%）平均與最大
-- 峰日、峰時以交易量為準（與 3.交易量統計-峰日峰時 相同：全部業務分類、排除 FAC2FAS）；主機範圍與 1.1 相同（FSAP-RUNTIME：主地 4 台、異地 2 台）
-- 輸出 4 列：峰日平均、峰日最大、峰時平均、峰時最大；不依業務分類篩選
-- 平均以「分」為單位做整數運算（四捨五入、平手進位），結果固定；直接用 ROUND(AVG(...), 2) 時，平手的格子會因平行執行的加總順序不同而差 0.01
WITH params AS (
    -- [參數] 峰日、峰時以交易量為準（與報表 3 相同：全部業務分類、排除 FAC2FAS）
    SELECT
        '${targetMonth}' AS target_month,
        'FAC2FAS'        AS exclude_pr_id,
        'FSAP-RUNTIME'   AS app_name
),
-- 步驟 1：找出峰日（月內交易量最大的一天；平手取較早的日期）
PeakDay AS (
    SELECT tx_dt
    FROM v_rt_pr_hh24_clean
    CROSS JOIN params p
    WHERE tx_yyyymm = p.target_month AND PR_ID != p.exclude_pr_id
    GROUP BY tx_dt
    ORDER BY SUM(tx_cnt) DESC, tx_dt
    LIMIT 1
),
-- 步驟 2：找出峰時（峰日當天交易量最大的小時；平手取較早的小時）
PeakHour AS (
    SELECT tx_hour
    FROM v_rt_pr_hh24_clean
    CROSS JOIN params p
    WHERE tx_dt = (SELECT tx_dt FROM PeakDay) AND PR_ID != p.exclude_pr_id
    GROUP BY tx_hour
    ORDER BY SUM(tx_cnt) DESC, tx_hour
    LIMIT 1
),
-- 步驟 3：峰日全日的 CPU／Memory（依 IP 分主地、異地；平均＝各台日平均再取站點平均；最大＝站點內任一台的最大值）
DayRows AS (
    SELECT '日' AS scope, site,
           CAST((2 * SUM(CAST(ROUND(avg_cpu_rate * 100) AS BIGINT)) + COUNT(avg_cpu_rate)) // NULLIF(2 * COUNT(avg_cpu_rate), 0) AS DOUBLE) / 100 AS cpu_avg, CAST((2 * SUM(CAST(ROUND(avg_mem_rate * 100) AS BIGINT)) + COUNT(avg_mem_rate)) // NULLIF(2 * COUNT(avg_mem_rate), 0) AS DOUBLE) / 100 AS mem_avg,
           MAX(max_cpu_rate) AS cpu_max, MAX(max_mem_rate) AS mem_max
    FROM (
        SELECT CASE
                 WHEN TARGET_IP IN ('10.4.240.183', '10.4.240.184', '10.4.240.185', '10.4.240.186') THEN '主地'
                 WHEN TARGET_IP IN ('10.4.240.187', '10.4.240.188') THEN '異地'
               END AS site, *
        FROM v_prod_monitor_daily_count
        WHERE application = (SELECT app_name FROM params)
          AND log_date = (SELECT tx_dt FROM PeakDay)
    )
    WHERE site IS NOT NULL
    GROUP BY site
),
-- 步驟 4：峰時該小時的 CPU／Memory（同上，來源改為每小時統計）
HourRows AS (
    SELECT '時' AS scope, site,
           CAST((2 * SUM(CAST(ROUND(avg_cpu_rate * 100) AS BIGINT)) + COUNT(avg_cpu_rate)) // NULLIF(2 * COUNT(avg_cpu_rate), 0) AS DOUBLE) / 100 AS cpu_avg, CAST((2 * SUM(CAST(ROUND(avg_mem_rate * 100) AS BIGINT)) + COUNT(avg_mem_rate)) // NULLIF(2 * COUNT(avg_mem_rate), 0) AS DOUBLE) / 100 AS mem_avg,
           MAX(max_cpu_rate) AS cpu_max, MAX(max_mem_rate) AS mem_max
    FROM (
        SELECT CASE
                 WHEN TARGET_IP IN ('10.4.240.183', '10.4.240.184', '10.4.240.185', '10.4.240.186') THEN '主地'
                 WHEN TARGET_IP IN ('10.4.240.187', '10.4.240.188') THEN '異地'
               END AS site, *
        FROM v_prod_monitor_hourly_count
        WHERE application = (SELECT app_name FROM params)
          AND log_date = (SELECT tx_dt FROM PeakDay)
          AND log_hour = (SELECT tx_hour FROM PeakHour)
    )
    WHERE site IS NOT NULL
    GROUP BY site
),
-- 步驟 5：把「平均」「最大」各展成一列
LongRows AS (
    SELECT scope, '平均' AS stat, 1 AS ord, site, cpu_avg AS cpu, mem_avg AS mem FROM (SELECT * FROM DayRows UNION ALL SELECT * FROM HourRows)
    UNION ALL
    SELECT scope, '最大' AS stat, 2 AS ord, site, cpu_max AS cpu, mem_max AS mem FROM (SELECT * FROM DayRows UNION ALL SELECT * FROM HourRows)
)
SELECT
    'FSAP' AS "專案名稱",
    CASE l.scope
        WHEN '日' THEN strftime(d.tx_dt, '%m/%d') || ' 使用率(%)'
        ELSE strftime(d.tx_dt, '%m/%d') || ' ' || printf('%02d:00 至 %02d:59', h.tx_hour, h.tx_hour) || ' 使用率(%)'
    END AS "監控標的",
    l.stat AS "統計",
    MAX(CASE WHEN l.site = '主地' THEN l.cpu END) AS "主地_CPU",
    MAX(CASE WHEN l.site = '主地' THEN l.mem END) AS "主地_Memory",
    MAX(CASE WHEN l.site = '異地' THEN l.cpu END) AS "異地_CPU",
    MAX(CASE WHEN l.site = '異地' THEN l.mem END) AS "異地_Memory"
FROM LongRows l
CROSS JOIN PeakDay d
CROSS JOIN PeakHour h
GROUP BY l.scope, l.stat, l.ord, d.tx_dt, h.tx_hour
ORDER BY (l.scope = '時'), l.ord;
