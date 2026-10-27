package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import com.fsap.monitor.core.report.ReportGenerationService.QueryTable;
import com.fsap.monitor.infra.config.FsapProperties;

class ReportDefaultsTest {

    @Test
    void packagedDefaultsProduceFsapAndIncludeItsSixComparisonSheets() throws Exception {
        var sources = new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml"));
        var properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("fsap", Bindable.of(FsapProperties.class)).get();
        var request = new ReportGenerationRequest(null, false, "2026-10", null, null, null, null, null, null, null);
        var kinds = new ReportParameterDefaultsService(properties).resolve(request).targetBzkinds();
        assertThat(kinds).containsExactly("NCL", "NCB", "BIS", "FAC", "NIS", "FSAP");
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
                        new QueryTable(List.of("kind"), List.of(List.of(pass))));
            }
            tables.put(pass, reports);
        }

        var plan = ComparisonWorkbookPlanner.plan(config, tables, kinds);
        assertThat(plan.messages()).isEmpty();
        assertThat(plan.sheets()).hasSize(41);
        var fsapSheets = plan.sheets().stream().filter(sheet -> sheet.sheetName().startsWith("FSAP-")).toList();
        assertThat(fsapSheets).hasSize(6);
        assertThat(fsapSheets).allSatisfy(sheet ->
                assertThat(sheet.table().rows().get(0).get(0)).isEqualTo("FSAP"));
    }
}
