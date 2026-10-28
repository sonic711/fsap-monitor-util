package com.fsap.monitor.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

class ApplicationConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FsapProperties.class)
    static class SettingsOnly { }

    @Test
    void packagedConfigurationContainsStableDefaultsWithoutDeploymentOrBusinessValues() throws Exception {
        var sources = new YamlPropertySourceLoader().load("internal", new ClassPathResource("application.yml"));
        var source = sources.get(0);
        assertThat(source.getProperty("spring.application.name")).isEqualTo("fsap-monitor-util");
        assertThat(source.getProperty("jasypt.encryptor.bean")).isEqualTo("encryptorBean");
        assertThat(source.getProperty("fsap.paths.source-lake-dir")).isEqualTo("02_source_lake");
        assertThat(source.getProperty("fsap.views.max-rounds")).isEqualTo(3);
        for (String key : List.of("server.port", "remote.url", "fsap.paths.base-dir",
                "fsap.ingest.target-sheets[0]", "fsap.report.target-bzkinds[0]",
                "fsap.report.combined-workbook.all-sheets[0].data-source")) {
            assertThat(source.getProperty(key)).as(key).isNull();
        }
    }

    @Test
    void springLoadsExternalBusinessSettingsAndCommandLineOverridesWithoutChangingStableDefaults() {
        var application = new SpringApplication(SettingsOnly.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);
        application.setDefaultProperties(Map.of("spring.main.banner-mode", "off", "logging.level.root", "ERROR"));
        String external = Path.of("config/application.yml").toAbsolutePath().toUri().toString();
        try (var context = application.run("--spring.config.location=classpath:/application.yml",
                "--spring.config.additional-location=" + external,
                "--server.port=18091", "--fsap.paths.base-dir=/test/config-override")) {
            var properties = context.getBean(FsapProperties.class);
            assertThat(properties.getReport().getTargetBzkinds())
                    .containsExactly("NCL", "NCB", "BIS", "FAC", "FAS", "NIS", "FSAP");
            assertThat(properties.getReport().getCombinedWorkbook().getAllSheets()).hasSize(10);
            assertThat(properties.getReport().getCombinedWorkbook().getReports()).hasSize(8);
            properties.getReport().validateConfiguration();
            assertThat(properties.getIngest().getTargetSheets()).contains("PR_CFG");
            assertThat(properties.getPaths().getSourceLakeDir()).isEqualTo("02_source_lake");
            assertThat(properties.getPaths().getBaseDir()).isEqualTo("/test/config-override");
            assertThat(context.getEnvironment().getProperty("server.port")).isEqualTo("18091");
            assertThat(context.getEnvironment().getProperty("spring.application.name")).isEqualTo("fsap-monitor-util");
            assertThat(context.getEnvironment().containsProperty("remote.url")).isTrue();
        }
    }
}
