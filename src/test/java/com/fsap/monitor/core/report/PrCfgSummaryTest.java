package com.fsap.monitor.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

class PrCfgSummaryTest {

    private static Connection open() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE v_cfg_pr_bzkind "
                    + "(PR_ID VARCHAR, PR_BZKIND VARCHAR, PR_CATEGORY VARCHAR, snapshot_dt VARCHAR)");
            statement.execute("INSERT INTO v_cfg_pr_bzkind VALUES "
                    + "('G61', 'NCL', 'update', '20261027'), ('G0601', 'NCL', 'query', '20261027'), "
                    + "('G0063', 'NCL', 'update', '20261027'), "
                    + "('Z0450', 'NIS', 'query', '20261027'), "
                    + "('G0109', 'BIS', 'query', '20261027'), "
                    + "('X1', NULL, 'update', '20261027')");
            statement.execute("CREATE TABLE v_rt_pr_hh24_clean (PR_ID VARCHAR, tx_dt_str VARCHAR)");
            statement.execute("INSERT INTO v_rt_pr_hh24_clean VALUES "
                    + "('G61', '2026-10-05'), ('G61', '2026-10-06'), ('G0601', '2026-10-05'), "
                    + "('G0109', '2026-10-26'), ('G0063', '2026-09-30')");
        }
        return connection;
    }

    @Test
    void summarizesEachBzkindWithUpdateQueryAndActiveCounts() throws Exception {
        try (Connection connection = open()) {
            List<String> lines = PrCfgSummary.describe(connection, "2026-10");
            // 標題 + 4 個分類（NCL、BIS、NIS、無分類）+ 合計
            assertEquals(6, lines.size(), String.join("\n", lines));
            assertTrue(lines.get(0).contains("最新快照 20261027"), lines.get(0));
            assertEquals("[分類摘要] NCL：3 支（更新類 2、查詢類 1）；2026-10 有交易 2 支", lines.get(1));
            assertTrue(lines.contains("[分類摘要] BIS：1 支（更新類 0、查詢類 1）；2026-10 有交易 1 支"), String.join("\n", lines));
            assertTrue(lines.contains("[分類摘要] NIS：1 支（更新類 0、查詢類 1）；2026-10 有交易 0 支"), String.join("\n", lines));
            assertTrue(lines.contains("[分類摘要] （無分類）：1 支（更新類 1、查詢類 0）；2026-10 有交易 0 支"), String.join("\n", lines));
            assertEquals("[分類摘要] 合計：6 支（更新類 3、查詢類 3）；2026-10 有交易 3 支", lines.get(lines.size() - 1));
        }
    }

    @Test
    void activeOnlyCountsTheTargetMonth() throws Exception {
        try (Connection connection = open()) {
            List<String> lines = PrCfgSummary.describe(connection, "2026-09");
            // G0063 只有 9 月有交易
            assertEquals("[分類摘要] 合計：6 支（更新類 3、查詢類 3）；2026-09 有交易 1 支", lines.get(lines.size() - 1));
        }
    }

    @Test
    void neverThrowsWhenTheTransactionViewIsMissing() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE v_cfg_pr_bzkind "
                        + "(PR_ID VARCHAR, PR_BZKIND VARCHAR, PR_CATEGORY VARCHAR, snapshot_dt VARCHAR)");
            }
            List<String> lines = PrCfgSummary.describe(connection, "2026-10");
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).contains("不影響報表產出"), lines.get(0));
        }
    }
}
