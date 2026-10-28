# View SQL 說明

依 view 名稱查來源與用途。SQL 位於 `<base-dir>/03_sql_logic/views/`；更新方式見 [SQL邏輯目錄](SQL邏輯目錄.md)。

## 1. 業務元數據視圖 (Metadata)
| 視圖名稱 | 功能說明 | 數據來源 |
| :--- | :--- | :--- |
| `v_cfg_pr_info` | 交易詳細配置參數。 | `CFG_PR_INFO.csv` |
| `v_cfg_pr_bzkind` | 程式代號（`PR_ID`）對應名稱（`PR_NAME`）、業務分類（`PR_BZKIND`）與交易類別（`PR_CATEGORY`：`update`／`query`，由 `PR_TPKIND` 決定，2＝查詢、1＝交易）。報表 7、8 系列以 `JOIN v_cfg_pr_bzkind` 取類別與名稱。 | 數據湖 `PR_CFG/PR_CFG-*.jsonl.gz`，只取 `_dt` 最新的一整份快照；舊快照沒有 `PR_TPKIND` 時該欄為 `NULL`，`generate-report` 前置檢查會要求最新快照每筆都有 1 或 2，否則中止。 |
| `v_prod_eureka_registry_info` | 微服務節點註冊清單。 | `prod_eureka_registry_info.jsonl` |

## 2. 監控與日誌視圖 (Monitoring)
| 視圖名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| `v_prod_monitor_log` | 日誌內容清洗與標準化。 | 利用 `Replace` 清洗關鍵字，並使用 `TRY_CAST` 將字串轉為數值 (CPU, MEM, Disk)，最後加入 `QUALIFY` 去重。 |
| `v_prod_monitor_daily_count` | 每日資源使用統計。 | 關聯 Eureka 註冊資訊，以 `application` 與 `TARGET_IP` 為維度，計算資源指標。 |
| `v_prod_monitor_daily_jvm_count` | 每日 JVM 資源使用統計。 | 以 application、IP、port 與日期彙總 JVM 指標。 |
| `v_prod_monitor_hourly_count` | 每小時資源使用統計。 | 供 `monitor-data` 的 hourly 匯出使用。 |
| `v_prod_monitor_hourly_jvm_count` | 每小時 JVM 資源使用統計。 | 供 `monitor-data` 的 JVM hourly 匯出使用。 |

## 3. 交易效能分析視圖 (Transactional)
| 視圖名稱 | 功能說明 | 關鍵邏輯 |
| :--- | :--- | :--- |
| `v_rt_pr_hh24_clean` | 小時級交易明細 (高精度)。 | 解析 `CALTM` 欄位並計算跨年修正。這是目前報表的最核心數據源，並已加入 `QUALIFY` 去重。 |
| `v_rt_tmspt_clean` | 交易處理時間明細。 | 針對 `PR_ID` 進行編號正規化 (lpad)，方便進行跨類型對比。 |
| `v_rt_node_hh24_clean` | 節點級交易明細。 | 與小時級明細結構一致，額外納入 `NODE_ID` 維度。 |
| `v_rt_cnt_clean` | 日級交易統計清洗資料。 | 由 `RT_CNT` JSONL.GZ 轉為可查詢的日總量口徑；現行報表未直接使用。 |
| `v_rt_cnt_daily` / `v_rt_cnt_daily_with_tmspt` | 日級交易聚合。 | 供查詢與後續擴充使用。 |
| `v_rt_cnt_monthly` / `v_rt_cnt_monthly_no_fac2fas` / `v_rt_cnt_weekly` | 月 / 週級交易聚合。 | 供查詢與後續擴充使用。 |
| `v_monthly_transaction_stats` | 每月各交易類型統計。 | 第 8 頁整月統計的 view 化版本，提供交易總量、加權平均處理時間、處理時間峰日、峰時、佔比與實際交易天數，供 MOM 報表重用。 |

業務分類欄位 `pr_bzkind`：

- 適用 view：`v_rt_pr_hh24_clean`、`v_rt_cnt_clean`、`v_rt_node_hh24_clean`、`v_rt_tmspt_clean`、`v_rt_cnt_daily`、`v_rt_cnt_weekly`、`v_rt_cnt_monthly`、`v_monthly_transaction_stats`。
- 以 `LEFT JOIN v_cfg_pr_bzkind` 取得分類；不在 `PR_CFG` 或分類為空者歸「其他/待分類」。`LEFT JOIN` 不丟列，所以報表以 `ALL`（不篩選）執行時仍包含未分類程式。
- `PR_CFG` 每天是完整快照，數據湖保留每一天，`v_cfg_pr_bzkind` 只取 `_dt` 最新的一份；同一 `PR_ID` 只會對應一個分類。
- 同一最新日期有重複 PR_ID 時依 `_ingest_ts` 較新者優先。所有歷史交易月份都用最新分類，不是各月份當時的分類。
- `v_rt_tmspt_clean` 以原始 `PR_ID` 對應分類，不使用正規化後的 `pr_id_norm`（`G61` 正規化為 `G0061` 後會與 `PR_CFG` 的代號對不上）。
- `v_rt_cnt_daily`、`v_rt_cnt_weekly`、`v_rt_cnt_monthly` 的 `pr_bzkind` 同時是分組維度，聚合結果按分類分列。

`v_monthly_transaction_stats` 的重要口徑：

- 峰日：每個 `PR_ID` 在該月中每日平均處理時間最高的日期。
- 峰值處理時間：峰日當天的日平均處理時間。
- 峰時：該月內小時交易量最高的時段；若同量，取較早日期、較早小時。
- 月總量與佔比：以 `v_rt_pr_hh24_clean.tx_cnt` 彙總。
- 分類內佔比：分母為該分類當月總量（口徑同月總量，只改成同一分類內加總）；「交易量佔比」的分母仍是整個系統。
- 此 view 不排除 FAC2FAS。沒有 PR_CFG 對照的交易可能留在總量分母，但不出現在最後的交易明細。
- 查詢月份使用輸出欄位 `"年月"`，例如 `SELECT * FROM v_monthly_transaction_stats WHERE "年月" = '2026-10'`，不是內部 CTE 欄位 year_month。

## 4. 數據清理策略說明
依資料來源不同，部分清洗 view 會採用以下處理：
1.  **跨年修正**：交易資料 view 會比較解析日期與檔案日期，修正跨年檔案的日期誤差。
2.  **正規化**：將原始欄位轉為正確型別 (`DATE`、`TIMESTAMP`、`DECIMAL`)。
3.  **去重**：各 view 以不同鍵與排序選取一筆，不可一概稱為保留最新記錄。交易清洗通常優先較新來源日期；PR_CFG 優先匯入時間；MON_LOG 以種類、IP、port、監控時間去重，相同鍵未再指定以最新匯入者優先。
