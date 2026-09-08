# View SQL 說明

依 view 名稱查來源與用途。SQL 位於 `<base-dir>/03_sql_logic/views/`；更新方式見 [SQL邏輯目錄](SQL邏輯目錄.md)。

## 1. 業務元數據視圖 (Metadata)
| 視圖名稱 | 功能說明 | 數據來源 |
| :--- | :--- | :--- |
| `v_pr_info` | 交易代碼定義與名稱映射。 | `PR_INFO.csv` |
| `v_cfg_pr_info` | 交易詳細配置參數。 | `CFG_PR_INFO.csv` |
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

`v_monthly_transaction_stats` 的重要口徑：

- 峰日：每個 `PR_ID` 在該月中每日平均處理時間最高的日期。
- 峰值處理時間：峰日當天的日平均處理時間。
- 峰時：該月內小時交易量最高的時段；若同量，取較早日期、較早小時。
- 月總量與佔比：以 `v_rt_pr_hh24_clean.tx_cnt` 彙總。

## 4. 數據清理策略說明
依資料來源不同，部分清洗 view 會採用以下處理：
1.  **跨年修正**：交易資料 view 會比較解析日期與檔案日期，修正跨年檔案的日期誤差。
2.  **正規化**：將原始欄位轉為正確型別 (`DATE`、`TIMESTAMP`、`DECIMAL`)。
3.  **去重**：需要去重的來源以 `QUALIFY` 與 `ROW_NUMBER()` 依關鍵維度保留最新記錄；元資料與純聚合 view 不一定套用此步驟。
