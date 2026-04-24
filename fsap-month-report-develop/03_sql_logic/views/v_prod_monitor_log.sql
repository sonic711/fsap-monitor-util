CREATE OR REPLACE VIEW v_prod_monitor_log AS
-- 使用 CTE 來讓查詢更結構化
WITH cleaned_logs AS (
    SELECT
        MONITOR_KIND,
        TARGET_IP,
        -- 保留原始欄位結構
        TARGET_PORT,
        Replace(
            Replace(
                Replace(MON_CONTENT, 'JVM資源使用狀況-監控結果資訊：', ''),
                '虛擬伺服器資源使用狀況-監控結果資訊：', ''
            ),
            '系統產生 資料庫連線-監控結果資訊：', ''
        ) AS log_content_cleaned,
        -- 新格式時間解析
        TRY_CAST(CREATETIME AS TIMESTAMP) AS log_createtime,
        -- 原始 MON_CONTENT 內容
        MON_CONTENT,
        -- 新增 Metadata 欄位
        _file,
        _sheet,
        _dt,
        _ingest_ts
    FROM read_json_auto(
        -- [更新路徑] 使用相對路徑
        '02_source_lake/MON_LOG/MON_LOG-*.jsonl.gz',
        columns = {
            'MONITOR_KIND': 'VARCHAR',
            'TARGET_IP': 'VARCHAR',
            'TARGET_PORT': 'VARCHAR',
            'MON_CONTENT': 'VARCHAR',
            'CREATETIME': 'VARCHAR',
            '_file': 'VARCHAR',
            '_sheet': 'VARCHAR',
            '_dt': 'VARCHAR',
            '_ingest_ts': 'BIGINT'
        }
    )
    WHERE
        TARGET_IP IS NOT NULL 
        AND TARGET_IP <> ''        -- [指定要求] 確保 IP 不為空
        AND MONITOR_KIND IS NOT NULL -- 過濾系統訊息
),
-- 從清理過的資料中，提取詳細欄位 (完全保留原本的 CASE WHEN 邏輯與順序)
final_extraction AS (
    SELECT
        MONITOR_KIND,
        TARGET_IP,
        TARGET_PORT,
        log_createtime,
        
        -- 1. 提取 CPU 使用率 (處理 'N/A%' 的情況)
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(Replace(NULLIF(regexp_extract(log_content_cleaned, 'usedCPURate:([^,]+)', 1), 'N/A%'), '%', '') AS DECIMAL(5, 2)) 
            ELSE NULL
        END AS used_cpu_rate,

        -- 2. 提取總記憶體
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(regexp_extract(log_content_cleaned, 'totalMem:([^,]+)', 1) AS DECIMAL(10, 2))
            ELSE NULL
        END AS total_mem,

        -- 3. 提取可用記憶體
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(regexp_extract(log_content_cleaned, 'freeMem:([^,]+)', 1) AS DECIMAL(10, 2))
            ELSE NULL
        END AS free_mem,

        -- 4. 提取記憶體使用率
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(Replace(regexp_extract(log_content_cleaned, 'usedMemRate:([^,]+)', 1), '%', '') AS DECIMAL(5, 2)) 
            ELSE NULL
        END AS used_mem_rate,
        
        -- 5. 提取總磁碟空間
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(regexp_extract(log_content_cleaned, 'totalDisk:([^,]+)', 1) AS DECIMAL(10, 2))
            ELSE NULL
        END AS total_disk,
        
        -- 6. 提取可用磁碟空間
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(regexp_extract(log_content_cleaned, 'freeDisk:([^,]+)', 1) AS DECIMAL(10, 2))
            ELSE NULL
        END AS free_disk,

        -- 7. 提取磁碟使用率
        CASE 
            WHEN MONITOR_KIND = 'SERVER' THEN 
                TRY_CAST(Replace(regexp_extract(log_content_cleaned, 'usedDiskRate:([^,]+)', 1), '%', '') AS DECIMAL(5, 2)) 
            ELSE NULL
        END AS used_disk_rate,

        -- JVM 指標欄位 (保持原邏輯)
        CASE 
            WHEN MONITOR_KIND = 'JVM' THEN TRY_CAST(regexp_extract(log_content_cleaned, 'usedHeapMem:([^,]+)', 1) AS DECIMAL(10, 2))
            ELSE NULL
        END AS used_heap_mem,

        CASE 
            WHEN MONITOR_KIND = 'JVM' THEN TRY_CAST(regexp_extract(log_content_cleaned, 'maxHeapMem:([^,]+)', 1) AS DECIMAL(10, 2))
            ELSE NULL
        END AS max_heap_mem,
        
        CASE 
            WHEN MONITOR_KIND = 'JVM' THEN TRY_CAST(Replace(regexp_extract(log_content_cleaned, 'usedMemRate:([^,]+)', 1), '%', '') AS DECIMAL(5, 2))
            ELSE NULL
        END AS used_heap_mem_rate,

        -- DB_CONN 指標欄位
        CASE 
            WHEN MONITOR_KIND = 'DB_CONN' THEN regexp_extract(log_content_cleaned, 'dbConnStatus:(.+)', 1)
            ELSE NULL
        END AS db_conn_status,

        -- 保留原始內容
        MON_CONTENT AS log_content,

        -- [末尾新增欄位]
        _file AS source_file,
        _sheet AS source_sheet,
        _dt AS report_dt,
        _ingest_ts AS ingest_timestamp
    FROM cleaned_logs
)
SELECT * FROM final_extraction
-- 重複項過濾
QUALIFY ROW_NUMBER() OVER(PARTITION BY MONITOR_KIND, TARGET_IP, TARGET_PORT, log_createtime ORDER BY log_createtime) = 1;
