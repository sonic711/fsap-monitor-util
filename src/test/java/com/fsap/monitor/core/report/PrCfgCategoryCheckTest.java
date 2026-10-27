package com.fsap.monitor.core.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

class PrCfgCategoryCheckTest {

    private static Connection open(String... insertValues) throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE v_cfg_pr_bzkind (PR_ID VARCHAR, PR_TPKIND INTEGER, PR_CATEGORY VARCHAR, snapshot_dt VARCHAR)");
            for (String values : insertValues) {
                statement.execute("INSERT INTO v_cfg_pr_bzkind VALUES " + values);
            }
        }
        return connection;
    }

    @Test
    void passesWhenEveryRowHasQueryOrUpdate() throws Exception {
        try (Connection connection = open("('G61', 1, 'update', '20261019'), ('G0601', 2, 'query', '20261019')")) {
            assertTrue(PrCfgCategoryCheck.findProblems(connection).isEmpty());
        }
    }

    @Test
    void failsWhenTpkindIsMissingOrOutOfRange() throws Exception {
        try (Connection connection = open(
                "('G61', 1, 'update', '20261019'), ('A3', NULL, NULL, '20261019'), ('A4', 3, NULL, '20261019')")) {
            List<String> problems = PrCfgCategoryCheck.findProblems(connection);
            assertEquals(3, problems.size());
            assertTrue(problems.get(0).contains("2 筆（共 3 筆）"), problems.get(0));
            assertTrue(problems.get(1).contains("A3（PR_TPKIND=空）"), problems.get(1));
            assertTrue(problems.get(1).contains("A4（PR_TPKIND=3）"), problems.get(1));
        }
    }

    @Test
    void failsWhenThereIsNoSnapshot() throws Exception {
        try (Connection connection = open()) {
            List<String> problems = PrCfgCategoryCheck.findProblems(connection);
            assertEquals(1, problems.size());
            assertTrue(problems.get(0).contains("找不到 PR_CFG 快照"), problems.get(0));
        }
    }

    @Test
    void reportsWhenViewLacksTheCategoryColumn() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE v_cfg_pr_bzkind (PR_ID VARCHAR, snapshot_dt VARCHAR)");
            }
            List<String> problems = PrCfgCategoryCheck.findProblems(connection);
            assertEquals(1, problems.size());
            assertTrue(problems.get(0).contains("sync-views"), problems.get(0));
        }
    }
}
