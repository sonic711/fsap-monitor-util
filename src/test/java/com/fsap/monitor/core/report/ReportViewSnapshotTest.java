package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportViewSnapshotTest {

    @TempDir
    Path directory;

    @Test
    void snapshotsAreConnectionLocalAndDependentViewsUseTheSnapshot() throws Exception {
        String url = "jdbc:duckdb:" + directory.resolve("snapshot.duckdb");
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE source (tx_cnt BIGINT, amount DECIMAL(12,2), optional VARCHAR)");
            statement.execute("INSERT INTO source VALUES (10, 12.34, NULL)");
            statement.execute("CREATE VIEW v_rt_pr_hh24_clean AS SELECT * FROM source");
            statement.execute("CREATE VIEW v_monthly_transaction_stats AS SELECT SUM(tx_cnt) AS total FROM v_rt_pr_hh24_clean");

            assertThat(ReportViewSnapshot.prepare(connection)).isEqualTo(2);
            statement.execute("INSERT INTO source VALUES (20, 45.67, 'new')");
            try (var rows = statement.executeQuery("SELECT * FROM v_rt_pr_hh24_clean")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("tx_cnt")).isEqualTo(10);
                assertThat(rows.getBigDecimal("amount")).isEqualByComparingTo("12.34");
                assertThat(rows.getObject("optional")).isNull();
                assertThat(rows.next()).isFalse();
            }
            try (var rows = statement.executeQuery("SELECT total FROM v_monthly_transaction_stats")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(10);
            }
        }

        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT total FROM v_monthly_transaction_stats")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(30);
            }
            assertThat(ReportViewSnapshot.prepare(connection)).isEqualTo(2);
            try (var rows = statement.executeQuery("SELECT total FROM v_monthly_transaction_stats")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(30);
            }
        }
    }

    @Test
    void skipsMissingViewsAndDoesNotShadowExistingTablesOrUnrelatedViews() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:duckdb:"); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE v_cfg_pr_bzkind AS SELECT 1 AS id");
            statement.execute("CREATE VIEW unrelated_view AS SELECT * FROM v_cfg_pr_bzkind");
            assertThat(ReportViewSnapshot.prepare(connection)).isZero();
            statement.execute("INSERT INTO v_cfg_pr_bzkind VALUES (2)");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM unrelated_view")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}
