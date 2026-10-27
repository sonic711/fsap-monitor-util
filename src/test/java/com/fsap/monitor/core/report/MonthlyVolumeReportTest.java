package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;

class MonthlyVolumeReportTest {

    @Test
    void systemLabelMatchesTheFilteredBusinessKindAndAllRemainsUnfiltered() throws Exception {
        String template = Files.readString(Path.of(
                "fsap-month-report-develop/03_sql_logic/reports/1.交易量統計-當月.sql"));
        List<String> kinds = List.of("NCL", "NCB", "BIS", "FAC", "NIS", "FSAP");
        try (var connection = DriverManager.getConnection("jdbc:duckdb:");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE v_rt_pr_hh24_clean "
                    + "(PR_ID VARCHAR, pr_bzkind VARCHAR, tx_dt_str VARCHAR, "
                    + "tx_yyyymm VARCHAR, tx_hour INTEGER, tx_cnt BIGINT)");
            try (var insert = connection.prepareStatement(
                    "INSERT INTO v_rt_pr_hh24_clean VALUES (?, ?, '2026-10-01', '2026-10', 10, ?)")) {
                for (int i = 0; i < kinds.size(); i++) {
                    insert.setString(1, "P" + i);
                    insert.setString(2, kinds.get(i));
                    insert.setLong(3, (i + 1) * 100L);
                    insert.executeUpdate();
                }
            }
            statement.execute("INSERT INTO v_rt_pr_hh24_clean VALUES "
                    + "('FAC2FAS', 'FSAP', '2026-10-01', '2026-10', 10, 9999), "
                    + "('P_OLD', 'FSAP', '2026-09-01', '2026-09', 10, 9999), "
                    + "('P_OTHER', 'FAS', '2026-10-01', '2026-10', 10, 700)");

            var request = new ReportGenerationRequest(null, false, "2026-10", null, null, null, null, null, null, null);
            for (String kind : List.of("ALL", "NCL", "NCB", "BIS", "FAC", "NIS", "FSAP")) {
                var parameters = request.templateParameters(kind);
                String sql = template.replace("${targetBzkind}", parameters.get("targetBzkind"))
                        .replace("${targetMonth}", parameters.get("targetMonth"));
                try (var result = statement.executeQuery(sql)) {
                    assertThat(result.next()).as(kind).isTrue();
                    assertThat(result.getString("系統")).isEqualTo("ALL".equals(kind) ? "全系統（ALL）" : kind);
                    long expected = "ALL".equals(kind) ? 2800L : (kinds.indexOf(kind) + 1) * 100L;
                    assertThat(result.getLong("月交易總量")).as(kind).isEqualTo(expected);
                    assertThat(result.getLong("峰日交易量")).as(kind).isEqualTo(expected);
                    assertThat(result.getLong("峰時交易量")).as(kind).isEqualTo(expected);
                    assertThat(result.getString("峰日峰時區間")).isEqualTo("10:00~10:59");
                    assertThat(result.next()).isFalse();
                }
            }
        }
    }
}
