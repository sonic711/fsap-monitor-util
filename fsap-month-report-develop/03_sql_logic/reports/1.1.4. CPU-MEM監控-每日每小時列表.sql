-- 1.1.4 CPU-MEM監控-每日每小時列表：區間內每一天、每一小時各一列的交易量與主地／異地 CPU、Memory 使用率（%）平均與最大
-- 日期區間沿用 1.1.1 的 ${rangeStartDate}／${rangeEndDate}；列以監控資料實際有的日期小時為準（某小時完全沒有監控資料時該列不會出現）
-- 交易量沿用 3.交易量統計-峰日峰時 的口徑（全部業務分類、排除 FAC2FAS）；主機範圍與 1.1 相同；不依業務分類篩選
-- 平均以「分」為單位做整數運算（四捨五入、平手進位），結果固定；直接用 ROUND(AVG(...), 2) 時，平手的格子會因平行執行的加總順序不同而差 0.01
WITH params AS (
    -- [參數] 日期區間沿用 1.1.1 的 rangeStartDate／rangeEndDate（未指定時為目標月首日與末日）
    SELECT
        '${rangeStartDate}' AS start_date,
        '${rangeEndDate}'   AS end_date,
        'FAC2FAS'           AS exclude_pr_id,
        'FSAP-RUNTIME'      AS app_name
),
-- 步驟 1：區間內每日每小時的交易量
TxHourly AS (
    -- 每日每小時交易量（與報表 3、1.1.3 相同：全部業務分類、排除 FAC2FAS）
    SELECT CAST(tx_dt AS DATE) AS tx_date, tx_hour, SUM(tx_cnt) AS tx_cnt
    FROM v_rt_pr_hh24_clean
    CROSS JOIN params p
    WHERE CAST(CAST(tx_dt AS DATE) AS VARCHAR) BETWEEN p.start_date AND p.end_date
      AND PR_ID != p.exclude_pr_id
    GROUP BY 1, 2
),
-- 步驟 2：區間內每日每小時、各站點的 CPU／Memory（平均＝各台小時平均再取站點平均；最大＝站點內任一台的最大值），橫向展開成八欄
MonHourly AS (
    SELECT log_date, log_hour,
           MAX(CASE WHEN site = '主地' THEN cpu_avg END) AS m_cpu_avg, MAX(CASE WHEN site = '主地' THEN cpu_max END) AS m_cpu_max,
           MAX(CASE WHEN site = '主地' THEN mem_avg END) AS m_mem_avg, MAX(CASE WHEN site = '主地' THEN mem_max END) AS m_mem_max,
           MAX(CASE WHEN site = '異地' THEN cpu_avg END) AS d_cpu_avg, MAX(CASE WHEN site = '異地' THEN cpu_max END) AS d_cpu_max,
           MAX(CASE WHEN site = '異地' THEN mem_avg END) AS d_mem_avg, MAX(CASE WHEN site = '異地' THEN mem_max END) AS d_mem_max
    FROM (
        SELECT log_date, log_hour, site,
               CAST((2 * SUM(CAST(ROUND(avg_cpu_rate * 100) AS BIGINT)) + COUNT(avg_cpu_rate)) // NULLIF(2 * COUNT(avg_cpu_rate), 0) AS DOUBLE) / 100 AS cpu_avg, MAX(max_cpu_rate) AS cpu_max,
               CAST((2 * SUM(CAST(ROUND(avg_mem_rate * 100) AS BIGINT)) + COUNT(avg_mem_rate)) // NULLIF(2 * COUNT(avg_mem_rate), 0) AS DOUBLE) / 100 AS mem_avg, MAX(max_mem_rate) AS mem_max
        FROM (
            SELECT CASE
                     WHEN TARGET_IP IN ('10.4.240.183', '10.4.240.184', '10.4.240.185', '10.4.240.186') THEN '主地'
                     WHEN TARGET_IP IN ('10.4.240.187', '10.4.240.188') THEN '異地'
                   END AS site, *
            FROM v_prod_monitor_hourly_count
            CROSS JOIN params p
            WHERE application = p.app_name
              AND CAST(log_date AS VARCHAR) BETWEEN p.start_date AND p.end_date
        )
        WHERE site IS NOT NULL
        GROUP BY log_date, log_hour, site
    )
    GROUP BY log_date, log_hour
)
SELECT
    CAST(m.log_date AS VARCHAR) AS "日期",
    printf('%02d:00 至 %02d:59', m.log_hour, m.log_hour) AS "小時",
    COALESCE(tx.tx_cnt, 0) AS "交易量",
    m.m_cpu_avg AS "主地_CPU_平均", m.m_cpu_max AS "主地_CPU_最大",
    m.m_mem_avg AS "主地_Memory_平均", m.m_mem_max AS "主地_Memory_最大",
    m.d_cpu_avg AS "異地_CPU_平均", m.d_cpu_max AS "異地_CPU_最大",
    m.d_mem_avg AS "異地_Memory_平均", m.d_mem_max AS "異地_Memory_最大"
FROM MonHourly m
LEFT JOIN TxHourly tx ON tx.tx_date = m.log_date AND tx.tx_hour = m.log_hour
ORDER BY m.log_date, m.log_hour;
