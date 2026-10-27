package com.fsap.monitor.core.report;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 檢查 PR_CFG 最新快照的交易類別來源。
 *
 * <p>交易類別（update／query）由 PR_CFG 的 PR_TPKIND 決定（2＝查詢、1＝交易），不再使用 PR_INFO.csv。
 * 最新快照缺 PR_TPKIND、或有空值與 1、2 以外的值時，報表 7、8 系列會沒有類別，
 * 所以在產報前檢查並中止，避免靜默產出空報表。
 */
final class PrCfgCategoryCheck {

    private static final int SAMPLE_LIMIT = 10;

    private PrCfgCategoryCheck() {
    }

    /**
     * @return 問題說明（第一則是摘要）；空清單表示通過
     */
    static List<String> findProblems(Connection connection) {
        List<String> problems = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            String snapshot = null;
            long total = 0;
            long invalid = 0;
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT MAX(snapshot_dt), COUNT(*), COUNT(*) FILTER (WHERE PR_CATEGORY IS NULL) FROM v_cfg_pr_bzkind")) {
                if (resultSet.next()) {
                    snapshot = resultSet.getString(1);
                    total = resultSet.getLong(2);
                    invalid = resultSet.getLong(3);
                }
            }
            if (total == 0) {
                problems.add("[前置檢查] 找不到 PR_CFG 快照（02_source_lake/PR_CFG），無法決定交易類別，已中止。"
                        + "請先執行 ingest 匯入含 PR_CFG 分頁的每日 Excel。");
                return problems;
            }
            if (invalid == 0) {
                return problems;
            }
            problems.add("[前置檢查] PR_CFG 最新快照 " + snapshot + " 有 " + invalid + " 筆（共 " + total
                    + " 筆）沒有有效的 PR_TPKIND（必須是 1 或 2），已中止。交易類別（update／query）由 PR_TPKIND 決定（2＝查詢、1＝交易）。");
            List<String> samples = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT PR_ID, PR_TPKIND FROM v_cfg_pr_bzkind WHERE PR_CATEGORY IS NULL ORDER BY PR_ID LIMIT " + SAMPLE_LIMIT)) {
                while (resultSet.next()) {
                    String value = resultSet.getObject(2) == null ? "空" : resultSet.getString(2);
                    samples.add(resultSet.getString(1) + "（PR_TPKIND=" + value + "）");
                }
            }
            problems.add("例（最多 " + SAMPLE_LIMIT + " 筆）：" + String.join("、", samples));
            problems.add("請確認每日 Excel 的 PR_CFG 分頁帶有 PR_TPKIND 欄位，且已重新執行 ingest 匯入。");
        } catch (SQLException exception) {
            problems.add("[前置檢查] 無法讀取 PR_CFG 的交易類別：" + exception.getMessage()
                    + "（請確認 sync-views 已使用新版 v_cfg_pr_bzkind）");
        }
        return problems;
    }
}
