package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fsap.monitor.core.report.ReportGenerationService.QueryTable;
import com.fsap.monitor.support.TestApplicationSettings;

class ReportDefaultsTest {

    @Test
    void externalSettingsProduceFsapAndUsePivotForDailyAndPeakComparisonSheets() throws Exception {
        var properties = TestApplicationSettings.load();
        var request = new ReportGenerationRequest(null, false, "2026-10", null, null, null, null, null, null, null);
        var kinds = new ReportParameterDefaultsService(properties).resolve(request).targetBzkinds();
        assertThat(kinds).containsExactly("NCL", "NCB", "BIS", "FAC", "FAS", "NIS", "FSAP");
        var config = properties.getReport().getCombinedWorkbook();
        assertThat(config.getBzkinds()).containsExactlyElementsOf(kinds);

        Map<String, Map<String, QueryTable>> tables = new LinkedHashMap<>();
        List<String> passes = new ArrayList<>(List.of("ALL"));
        passes.addAll(kinds);
        for (String pass : passes) {
            Map<String, QueryTable> reports = new LinkedHashMap<>();
            var specs = "ALL".equals(pass) ? config.getAllSheets() : config.getReports();
            for (var spec : specs) {
                reports.put(ComparisonWorkbookPlanner.reportKey(spec.getDataSource()),
                        new QueryTable(List.of("kind", "source"), List.of(List.of(pass, spec.getDataSource()))));
            }
            tables.put(pass, reports);
        }

        var plan = ComparisonWorkbookPlanner.plan(config, tables, kinds);
        assertThat(plan.messages()).isEmpty();
        assertThat(plan.sheets()).hasSize(66);
        assertThat(plan.sheets().get(1).sheetName()).isEqualTo("總覽-CPU-MEM峰日峰時");
        assertThat(plan.sheets().get(1).table().rows().get(0))
                .containsExactly("ALL", "1.1.2. CPU-MEM監控-峰日峰時.csv");
        assertThat(plan.sheets().stream().filter(sheet -> sheet.sheetName().contains("CPU-MEM")).toList())
                .hasSize(1);
        Map<String, String> top6Sources = Map.of(
                "總覽-TOP6交易類", "7.1.交易量統計-TOP6交易類.csv",
                "總覽-TOP6查詢類", "7.2.交易量統計-TOP6查詢類.csv");
        for (var top6 : top6Sources.entrySet()) {
            var sheet = plan.sheets().stream()
                    .filter(item -> item.sheetName().equals(top6.getKey())).findFirst().orElseThrow();
            assertThat(sheet.table().rows().get(0)).containsExactly("ALL", top6.getValue());
        }
        assertThat(plan.sheets().subList(7, 10)).extracting(sheet -> sheet.sheetName())
                .containsExactly("總覽-TOP6交易類", "總覽-TOP6查詢類", "總覽-MOM交易量比較");
        var fsapSheets = plan.sheets().stream().filter(sheet -> sheet.sheetName().startsWith("FSAP-")).toList();
        assertThat(fsapSheets).hasSize(8);
        assertThat(fsapSheets).allSatisfy(sheet ->
                assertThat(sheet.table().rows().get(0).get(0)).isEqualTo("FSAP"));
        var fasSheets = plan.sheets().stream().filter(sheet -> sheet.sheetName().startsWith("FAS-")).toList();
        assertThat(fasSheets).hasSize(8);
        assertThat(fasSheets).allSatisfy(sheet ->
                assertThat(sheet.table().rows().get(0).get(0)).isEqualTo("FAS"));
        Map<String, String> pivotSources = Map.of(
                "當月每日", "2.1.交易量統計-當月每日-Pivot.csv",
                "峰日峰時", "3.1.交易量統計-峰日峰時-Pivot.csv",
                "歷史峰日", "5.1.交易量統計-歷史峰日-Pivot.csv",
                "歷史峰日峰時", "6.1.交易量統計-歷史峰日峰時-Pivot.csv");
        List<String> prefixes = new ArrayList<>(List.of("總覽"));
        prefixes.addAll(kinds);
        for (String prefix : prefixes) {
            for (var pivot : pivotSources.entrySet()) {
                var sheet = plan.sheets().stream()
                        .filter(item -> item.sheetName().equals(prefix + "-" + pivot.getKey())).findFirst().orElseThrow();
                assertThat(sheet.table().rows().get(0).get(1)).isEqualTo(pivot.getValue());
            }
        }
    }
}
