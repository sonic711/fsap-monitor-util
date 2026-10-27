-- 1.1.3 CPU-MEM監控-峰日每小時：峰日 00 至 23 點每小時的交易量與主地／異地 CPU、Memory 使用率（%）平均與最大
-- 峰日以交易量為準（與 3.交易量統計-峰日峰時 相同：全部業務分類、排除 FAC2FAS）；固定輸出 24 列，某小時沒有資料時該列為空值
-- 主機範圍與 1.1 相同（FSAP-RUNTIME：主地 4 台、異地 2 台）；不依業務分類篩選
-- 平均以「分」為單位做整數運算（四捨五入、平手進位），結果固定；直接用 ROUND(AVG(...), 2) 時，平手的格子會因平行執行的加總順序不同而差 0.01
WITH params AS (
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
-- 步驟 2：峰日每小時的交易量
TxHourly AS (
    SELECT tx_hour, SUM(tx_cnt) AS tx_cnt
    FROM v_rt_pr_hh24_clean
    CROSS JOIN params p
    WHERE tx_dt = (SELECT tx_dt FROM PeakDay) AND PR_ID != p.exclude_pr_id
    GROUP BY tx_hour
),
-- 步驟 3：峰日每小時、各站點的 CPU／Memory（平均＝各台小時平均再取站點平均；最大＝站點內任一台的最大值），橫向展開成八欄
MonHourly AS (
    SELECT log_hour,
           MAX(CASE WHEN site = '主地' THEN cpu_avg END) AS m_cpu_avg, MAX(CASE WHEN site = '主地' THEN cpu_max END) AS m_cpu_max,
           MAX(CASE WHEN site = '主地' THEN mem_avg END) AS m_mem_avg, MAX(CASE WHEN site = '主地' THEN mem_max END) AS m_mem_max,
           MAX(CASE WHEN site = '異地' THEN cpu_avg END) AS d_cpu_avg, MAX(CASE WHEN site = '異地' THEN cpu_max END) AS d_cpu_max,
           MAX(CASE WHEN site = '異地' THEN mem_avg END) AS d_mem_avg, MAX(CASE WHEN site = '異地' THEN mem_max END) AS d_mem_max
    FROM (
        SELECT log_hour, site,
               CAST((2 * SUM(CAST(ROUND(avg_cpu_rate * 100) AS BIGINT)) + COUNT(avg_cpu_rate)) // NULLIF(2 * COUNT(avg_cpu_rate), 0) AS DOUBLE) / 100 AS cpu_avg, MAX(max_cpu_rate) AS cpu_max,
               CAST((2 * SUM(CAST(ROUND(avg_mem_rate * 100) AS BIGINT)) + COUNT(avg_mem_rate)) // NULLIF(2 * COUNT(avg_mem_rate), 0) AS DOUBLE) / 100 AS mem_avg, MAX(max_mem_rate) AS mem_max
        FROM (
            SELECT CASE
                     WHEN TARGET_IP IN ('10.4.240.183', '10.4.240.184', '10.4.240.185', '10.4.240.186') THEN '主地'
                     WHEN TARGET_IP IN ('10.4.240.187', '10.4.240.188') THEN '異地'
                   END AS site, *
            FROM v_prod_monitor_hourly_count
            WHERE application = (SELECT app_name FROM params)
              AND log_date = (SELECT tx_dt FROM PeakDay)
        )
        WHERE site IS NOT NULL
        GROUP BY log_hour, site
    )
    GROUP BY log_hour
)
SELECT
    strftime((SELECT tx_dt FROM PeakDay), '%Y-%m-%d') AS "日期",
    printf('%02d:00 至 %02d:59', g.h, g.h) AS "小時",
    COALESCE(tx.tx_cnt, 0) AS "交易量",
    mon.m_cpu_avg AS "主地_CPU_平均", mon.m_cpu_max AS "主地_CPU_最大",
    mon.m_mem_avg AS "主地_Memory_平均", mon.m_mem_max AS "主地_Memory_最大",
    mon.d_cpu_avg AS "異地_CPU_平均", mon.d_cpu_max AS "異地_CPU_最大",
    mon.d_mem_avg AS "異地_Memory_平均", mon.d_mem_max AS "異地_Memory_最大"
FROM generate_series(0, 23) AS g(h)
LEFT JOIN TxHourly tx ON tx.tx_hour = g.h
LEFT JOIN MonHourly mon ON mon.log_hour = g.h
ORDER BY g.h;
