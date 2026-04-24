CREATE OR REPLACE VIEW v_prod_monitor_daily_jvm_count AS
-- CTE 1: 預先準備好 IP/PORT 與 Application Name 的對應表
WITH app_info AS (
    SELECT DISTINCT
        application,
        ipAddr,
        port
    FROM 
        v_prod_eureka_registry_info
),
-- CTE 2: 篩選 JVM 數據並選取核心欄位
prepped_jvm_logs AS (
    SELECT
        TARGET_IP,
        TARGET_PORT,
        log_createtime,
        -- 直接使用 v_prod_monitor_log 中已解析的欄位
        used_heap_mem AS used_heap_mem_mb,
        max_heap_mem AS max_heap_mem_mb,
        used_heap_mem_rate AS used_heap_mem_rate
    FROM 
        v_prod_monitor_log
    WHERE
        MONITOR_KIND = 'JVM'
        -- 確保核心指標欄位非空
        AND used_heap_mem IS NOT NULL
)
-- 主查詢：進行 JOIN 和按「日」彙總
SELECT
    -- 分組欄位
    info.application,
    logs.TARGET_IP,
    logs.TARGET_PORT,
    
    -- 【核心修改】將時間截斷到「天」，並命名為 log_date
    DATE_TRUNC('day', logs.log_createtime) AS log_date,

    -- 統計總筆數 (一天內的採樣點總數)
    COUNT(1) AS record_count,

    -- 已使用堆記憶體 (usedHeapMem) 的統計
    ROUND(MAX(logs.used_heap_mem_mb), 2) AS max_used_heap_mem,
    ROUND(MIN(logs.used_heap_mem_mb), 2) AS min_used_heap_mem,
    ROUND(AVG(logs.used_heap_mem_mb), 2) AS avg_used_heap_mem,
    
    -- 最大堆記憶體 (maxHeapMem) 的統計
    ROUND(AVG(logs.max_heap_mem_mb), 2) AS avg_max_heap_mem,
    ROUND(MAX(logs.max_heap_mem_mb), 2) AS max_max_heap_mem,

    -- 堆記憶體使用率 (usedMemRate) 的統計
    MAX(logs.used_heap_mem_rate) AS max_used_heap_mem_rate,
    MIN(logs.used_heap_mem_rate) AS min_used_heap_rate,
    ROUND(AVG(logs.used_heap_mem_rate), 2) AS avg_used_heap_mem_rate
    
FROM 
    prepped_jvm_logs AS logs
LEFT JOIN 
    app_info AS info 
    -- 連接條件：IP 和 PORT
    ON logs.TARGET_IP = info.ipAddr AND logs.TARGET_PORT = info.port
GROUP BY
    info.application,
    logs.TARGET_IP,
    logs.TARGET_PORT,
    log_date  -- 按天分組 (使用新的欄位名稱)
ORDER BY
    log_date DESC,
    max_used_heap_mem_rate DESC
;
