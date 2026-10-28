package com.fsap.monitor.infra.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;

class ReportConfigurationTest {

    @Test
    void omittedSheetListIsRejectedEvenWhenKindsAreConfigured() {
        var properties = new FsapProperties();
        properties.getReport().setTargetBzkinds(List.of("FSAP"));
        properties.getReport().getCombinedWorkbook().setAllSheets(List.of());

        assertThatThrownBy(() -> properties.getReport().validateConfiguration())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fsap.report.combined-workbook.reports");
    }

    @Test
    void explicitlyEmptyYamlListsRemainAnIntentionalAllOnlyConfiguration() throws Exception {
        String yaml = """
                fsap:
                  report:
                    target-bzkinds: []
                    combined-workbook:
                      all-sheets: []
                      reports: []
                """;
        var sources = new YamlPropertySourceLoader().load("empty", new ByteArrayResource(yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("fsap", Bindable.of(FsapProperties.class)).get();
        properties.getReport().validateConfiguration();
    }
}
