package com.fsap.monitor.core.report;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 產報前把 PR_CFG 最新快照依業務分類（PR_BZKIND）彙總成幾行記錄，方便一眼看出各分類有幾支程式、
 * 其中更新類（update）與查詢類（query）各幾支，以及目標月份有交易的程式有幾支。
 *
 * <p>只是資訊，不影響報表內容：任何查詢失敗都只回一行說明，不會讓產報中止。
 * 「有交易」以 v_rt_pr_hh24_clean 在目標月份有資料為準。
 */
final class PrCfgSummary {

    static final String PREFIX = "[分類摘要] ";
    private static final String NO_BZKIND = "（無分類）";

    private PrCfgSummary() {
    }

    /**
     * @param targetMonth 目標月份，格式 yyyy-MM；空值時「有交易」一律視為 0
     * @return 要寫進執行記錄的行（第一行是標題，最後一行是合計）
     */
    static List<String> describe(Connection connection, String targetMonth) {
        List<String> lines = new ArrayList<>();
        String month = targetMonth == null ? "" : targetMonth;
        String sql = "SELECT coalesce(c.PR_BZKIND, '" + NO_BZKIND + "') AS bzkind, "
                + "COUNT(*) AS total, "
                + "COUNT(*) FILTER (WHERE c.PR_CATEGORY = 'update') AS updates, "
                + "COUNT(*) FILTER (WHERE c.PR_CATEGORY = 'query') AS queries, "
                + "COUNT(*) FILTER (WHERE c.PR_ID IN "
                + "(SELECT PR_ID FROM v_rt_pr_hh24_clean WHERE tx_dt_str LIKE ?)) AS active, "
                + "MAX(c.snapshot_dt) AS snapshot "
                + "FROM v_cfg_pr_bzkind c GROUP BY 1 ORDER BY total DESC, bzkind";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, month + "-%");
            long total = 0;
            long updates = 0;
            long queries = 0;
            long active = 0;
            String snapshot = null;
            List<String> rows = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    long rowTotal = resultSet.getLong(2);
                    long rowUpdates = resultSet.getLong(3);
                    long rowQueries = resultSet.getLong(4);
                    long rowActive = resultSet.getLong(5);
                    rows.add(PREFIX + resultSet.getString(1) + "：" + rowTotal + " 支（更新類 " + rowUpdates
                            + "、查詢類 " + rowQueries + "）；" + month + " 有交易 " + rowActive + " 支");
                    total += rowTotal;
                    updates += rowUpdates;
                    queries += rowQueries;
                    active += rowActive;
                    String rowSnapshot = resultSet.getString(6);
                    if (rowSnapshot != null && (snapshot == null || rowSnapshot.compareTo(snapshot) > 0)) {
                        snapshot = rowSnapshot;
                    }
                }
            }
            lines.add(PREFIX + "PR_CFG 最新快照 " + (snapshot == null ? "（無）" : snapshot)
                    + " 依業務分類彙總（有交易＝" + month + " 在 v_rt_pr_hh24_clean 有資料）");
            lines.addAll(rows);
            lines.add(PREFIX + "合計：" + total + " 支（更新類 " + updates + "、查詢類 " + queries + "）；"
                    + month + " 有交易 " + active + " 支");
        } catch (SQLException exception) {
            lines.clear();
            lines.add(PREFIX + "無法彙總 PR_CFG 分類（不影響報表產出）：" + exception.getMessage());
        }
        return lines;
    }
}
