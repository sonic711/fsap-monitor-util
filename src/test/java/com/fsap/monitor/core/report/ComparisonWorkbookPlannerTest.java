package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fsap.monitor.core.report.ReportGenerationService.QueryTable;
import com.fsap.monitor.infra.config.FsapProperties;

class ComparisonWorkbookPlannerTest {

    private static FsapProperties.SheetSpec spec(String sheetName, String dataSource) {
        FsapProperties.SheetSpec spec = new FsapProperties.SheetSpec();
        spec.setSheetName(sheetName);
        spec.setDataSource(dataSource);
        return spec;
    }

    private static QueryTable table(String marker) {
        return new QueryTable(List.of("c"), List.of(List.of(marker)));
    }

    private static FsapProperties.CombinedWorkbook config() {
        FsapProperties.CombinedWorkbook config = new FsapProperties.CombinedWorkbook();
        config.setAllSheets(List.of(spec("總覽-月量", "1.月量.csv")));
        config.setBzkinds(List.of("NCL", "NIS"));
        config.setReports(List.of(spec("{pr_bzkind}-月量", "1.月量.csv"), spec("{pr_bzkind}-明細", "7.明細.csv")));
        return config;
    }

    private static Map<String, Map<String, QueryTable>> tables(String... passes) {
        Map<String, Map<String, QueryTable>> result = new LinkedHashMap<>();
        for (String pass : passes) {
            Map<String, QueryTable> reports = new LinkedHashMap<>();
            reports.put("1.月量", table(pass + "-1"));
            reports.put("7.明細", table(pass + "-7"));
            result.put(pass, reports);
        }
        return result;
    }

    @Test
    void ordersAllSheetsFirstThenEachKindByReportOrder() {
        var plan = ComparisonWorkbookPlanner.plan(config(), tables("ALL", "NCL", "NIS"), List.of("NCL", "NIS"));
        assertThat(plan.sheets()).extracting(ComparisonWorkbookPlanner.SheetPlan::sheetName)
                .containsExactly("總覽-月量", "NCL-月量", "NCL-明細", "NIS-月量", "NIS-明細");
        assertThat(plan.sheets().get(0).table().rows().get(0).get(0)).isEqualTo("ALL-1");
        assertThat(plan.sheets().get(3).table().rows().get(0).get(0)).isEqualTo("NIS-1");
        assertThat(plan.messages()).isEmpty();
    }

    @Test
    void skipsKindThatWasNotProduced() {
        var plan = ComparisonWorkbookPlanner.plan(config(), tables("ALL", "NCL"), List.of("NCL"));
        assertThat(plan.sheets()).hasSize(3);
        assertThat(plan.messages()).singleElement().asString().contains("NIS");
    }

    @Test
    void skipsSheetWhenReportIsMissing() {
        var tables = tables("ALL", "NCL");
        tables.get("NCL").remove("7.明細");
        var plan = ComparisonWorkbookPlanner.plan(config(), tables, List.of("NCL"));
        assertThat(plan.sheets()).extracting(ComparisonWorkbookPlanner.SheetPlan::sheetName)
                .doesNotContain("NCL-明細");
        assertThat(plan.messages()).anyMatch(message -> message.contains("NCL-明細"));
    }

    @Test
    void usesExecutedKindsWhenConfigListsNone() {
        var config = config();
        config.setBzkinds(List.of());
        var plan = ComparisonWorkbookPlanner.plan(config, tables("ALL", "NIS"), List.of("NIS"));
        assertThat(plan.sheets()).extracting(ComparisonWorkbookPlanner.SheetPlan::sheetName)
                .containsExactly("總覽-月量", "NIS-月量", "NIS-明細");
    }

    @Test
    void truncatesLongNamesAndKeepsNamesUnique() {
        var config = config();
        config.setAllSheets(List.of());
        config.setBzkinds(List.of("NCL"));
        String longName = "{pr_bzkind}-" + "長".repeat(40);
        config.setReports(List.of(spec(longName, "1.月量.csv"), spec(longName, "7.明細.csv")));
        var plan = ComparisonWorkbookPlanner.plan(config, tables("ALL", "NCL"), List.of("NCL"));
        List<String> names = plan.sheets().stream().map(ComparisonWorkbookPlanner.SheetPlan::sheetName).toList();
        assertThat(names).hasSize(2).doesNotHaveDuplicates();
        assertThat(names).allMatch(name -> name.length() <= 31);
        assertThat(plan.messages()).hasSize(2);
    }

    @Test
    void acceptsDataSourceWithOrWithoutCsvExtension() {
        assertThat(ComparisonWorkbookPlanner.reportKey("1.月量.csv")).isEqualTo("1.月量");
        assertThat(ComparisonWorkbookPlanner.reportKey(" 1.月量 ")).isEqualTo("1.月量");
        assertThat(ComparisonWorkbookPlanner.reportKey(null)).isEmpty();
    }
}
