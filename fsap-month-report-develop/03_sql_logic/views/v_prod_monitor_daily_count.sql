CREATE OR REPLACE VIEW v_prod_monitor_daily_count AS
-- CTE 1: 預先準備好 IP 與 Application Name 的對應表
WITH app_info AS (
    SELECT DISTINCT
        application,
        ipAddr
    FROM v_prod_eureka_registry_info
),
-- CTE 2: [新增步驟] 預先處理日誌，計算出每筆紀錄的絕對使用量
prepped_logs AS (
    SELECT
        *,
        -- 計算已使用的磁碟空間 (GB/MB)
        (total_disk - free_disk) AS used_disk,
        -- 計算已使用的記憶體空間 (GB/MB)
        (total_mem - free_mem) AS used_mem
    FROM 
        v_prod_monitor_log
    WHERE
        -- 預先在此過濾，提升效率
        MONITOR_KIND = 'SERVER'
)
-- 主查詢：對預處理過的資料進行 JOIN 和彙總
SELECT
    -- 分組欄位
    info.application,
    logs.TARGET_IP,
    CAST(logs.log_createtime AS DATE) AS log_date,

    -- 統計總筆數
    COUNT(1) AS record_count,

    -- CPU 使用率 (%) 的統計
    MAX(logs.used_cpu_rate) AS max_cpu_rate,
    MIN(logs.used_cpu_rate) AS min_cpu_rate,
    ROUND(AVG(logs.used_cpu_rate), 2) AS avg_cpu_rate,

    -- 記憶體使用率 (%) 的統計
    MAX(logs.used_mem_rate) AS max_mem_rate,
    MIN(logs.used_mem_rate) AS min_mem_rate,
    ROUND(AVG(logs.used_mem_rate), 2) AS avg_mem_rate,
    
    -- [新增] 記憶體絕對使用量 (GB/MB) 的統計
    ROUND(AVG(logs.total_mem), 2) AS avg_total_mem,
    ROUND(MAX(logs.used_mem), 2) AS max_used_mem,
    ROUND(MIN(logs.used_mem), 2) AS min_used_mem,
    ROUND(AVG(logs.used_mem), 2) AS avg_used_mem,

    -- 磁碟使用率 (%) 的統計
    MAX(logs.used_disk_rate) AS max_disk_rate,
    MIN(logs.used_disk_rate) AS min_disk_rate,
    ROUND(AVG(logs.used_disk_rate), 2) AS avg_disk_rate,
    
    -- [新增] 磁碟絕對使用量 (GB/MB) 的統計
    ROUND(AVG(logs.total_disk), 2) AS avg_total_disk,
    ROUND(MAX(logs.used_disk), 2) AS max_used_disk,
    ROUND(MIN(logs.used_disk), 2) AS min_used_disk,
    ROUND(AVG(logs.used_disk), 2) AS avg_used_disk

FROM 
    prepped_logs AS logs -- 主查詢的來源改為 prepped_logs
LEFT JOIN 
    app_info AS info ON logs.TARGET_IP = info.ipAddr
-- WHERE 子句已移至 prepped_logs CTE 中
GROUP BY
    info.application,
    logs.TARGET_IP,
    log_date
;