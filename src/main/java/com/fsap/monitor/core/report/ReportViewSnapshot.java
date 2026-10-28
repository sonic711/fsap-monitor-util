package com.fsap.monitor.core.report;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本次產報連線內重用的資料，避免每支 SQL 反覆解壓並解析相同的數據湖。
 */
final class ReportViewSnapshot {

    private static final List<String> VIEWS = List.of(
            "v_cfg_pr_bzkind",
            "v_rt_pr_hh24_clean",
            "v_monthly_transaction_stats",
            "v_prod_monitor_daily_count",
            "v_prod_monitor_hourly_count"
    );

    private ReportViewSnapshot() { }

    static int prepare(Connection connection) throws SQLException {
        Set<String> available = new HashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT table_name FROM information_schema.views "
                        + "WHERE table_catalog = current_database() AND table_schema = 'main'")) {
            while (rows.next()) {
                available.add(rows.getString(1));
            }
        }

        int count = 0;
        for (String view : VIEWS) {
            if (!available.contains(view)) {
                continue;
            }
            // 名稱只取固定白名單；TEMP TABLE 僅遮蔽本連線查詢，不替換永久 view 或原始資料。
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE \"" + view + "\" AS SELECT * FROM main.\"" + view + "\"");
            }
            count++;
        }
        return count;
    }
}
