package com.fsap.monitor.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.artifact.ArtifactBrowseService;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.viewsync.ViewSyncService;
import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.infra.duckdb.DuckDbConnectionFactory;

class ReportOutputLayoutTest {

    @TempDir
    Path baseDirectory;

    private FsapProperties properties;
    private ProjectPathService paths;

    private ReportGenerationService service() throws Exception {
        var sources = new YamlPropertySourceLoader().load("defaults", new ClassPathResource("application.yml"));
        properties = new Binder(ConfigurationPropertySources.from(sources))
                .bind("fsap", Bindable.of(FsapProperties.class)).get();
        properties.getPaths().setBaseDir(baseDirectory.toString());
        paths = new ProjectPathService(properties);
        var factory = new DuckDbConnectionFactory(paths);
        try (var connection = factory.openConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE v_cfg_pr_bzkind "
                    + "(PR_ID VARCHAR, PR_BZKIND VARCHAR, PR_CATEGORY VARCHAR, snapshot_dt VARCHAR)");
            try (var insert = connection.prepareStatement(
                    "INSERT INTO v_cfg_pr_bzkind VALUES (?, ?, 'update', '20261027')")) {
                for (String kind : properties.getReport().getTargetBzkinds()) {
                    insert.setString(1, "P_" + kind);
                    insert.setString(2, kind);
                    insert.executeUpdate();
                }
            }
            statement.execute("CREATE TABLE v_rt_pr_hh24_clean (PR_ID VARCHAR, tx_dt_str VARCHAR)");
        }
        Files.createDirectories(paths.reportsDir());
        Set<String> reports = new LinkedHashSet<>();
        var config = properties.getReport().getCombinedWorkbook();
        config.getAllSheets().forEach(spec -> reports.add(ComparisonWorkbookPlanner.reportKey(spec.getDataSource())));
        config.getReports().forEach(spec -> reports.add(ComparisonWorkbookPlanner.reportKey(spec.getDataSource())));
        reports.add("2.交易量統計-當月每日");
        reports.add("3.交易量統計-峰日峰時");
        for (String report : reports) {
            Files.writeString(paths.reportsDir().resolve(report + ".sql"),
                    "SELECT '${targetBzkind}' AS kind, '" + report + "' AS source");
        }
        return new ReportGenerationService(paths, factory, new ViewSyncService(paths, factory, properties),
                new ReportParameterDefaultsService(properties), new ObjectMapper(), properties);
    }

    private ReportGenerationRequest request(List<String> kinds) {
        return new ReportGenerationRequest("202611020330", false, "2026-10", null, null, null, null, null, null, kinds);
    }

    @Test
    void finalWorkbookIsAtBatchRootAndAllArtifactsAreUnderSum() throws Exception {
        var service = service();
        var result = service.generate(request(null));
        Path root = paths.reportOutputDir().resolve("202611020330");
        String filename = "維運月度報表_10月彙總_202611020330.xlsx";
        assertThat(result.failures()).isEmpty();
        assertThat(result.workbookPath()).isEqualTo(root.resolve(filename)).isRegularFile();
        assertThat(result.comparisonWorkbookPath()).isEqualTo(result.workbookPath());
        assertThat(root.resolve("REPORT")).doesNotExist();
        assertThat(root.resolve("SUM").resolve(filename)).isRegularFile();
        assertThat(result.passResults().get(0).directory()).isEqualTo(root.resolve("SUM"));
        assertThat(result.passResults().get(0).workbookPath()).isEqualTo(root.resolve("SUM").resolve(filename));
        assertThat(root.resolve("SUM/2.1.交易量統計-當月每日-Pivot.csv")).isRegularFile();
        assertThat(root.resolve("FSAP/2.1.交易量統計-當月每日-Pivot.csv")).isRegularFile();
        try (var files = Files.list(root)) {
            assertThat(files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder(filename, "report-params.json");
        }
        try (var input = Files.newInputStream(result.workbookPath()); var workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(55);
            assertThat(workbook.getSheet("總覽-當月每日").getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("2.1.交易量統計-當月每日-Pivot");
            assertThat(workbook.getSheet("FSAP-歷史峰日峰時").getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("6.1.交易量統計-歷史峰日峰時-Pivot");
            assertThat(workbook.getSheet("FSAP-歷史峰日峰時").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("FSAP");
        }
        assertThat(new ArtifactBrowseService(paths).loadRecentReportBatches(1).get(0).files())
                .extracting(ArtifactBrowseService.FileView::filename).contains(filename);
    }

    @Test
    void allOnlyRunStillPublishesAnUploadableRootWorkbook() throws Exception {
        var result = service().generate(request(List.of()));
        assertThat(result.comparisonWorkbookPath()).isNull();
        assertThat(result.workbookPath()).isRegularFile();
        assertThat(Files.mismatch(result.workbookPath(), result.passResults().get(0).workbookPath())).isEqualTo(-1L);
        assertThat(result.runDirectory().resolve("REPORT")).doesNotExist();
    }

    @Test
    void disabledComparisonFallsBackToAllWorkbookWithoutChangingCategoryOutputs() throws Exception {
        var service = service();
        var config = properties.getReport().getCombinedWorkbook();
        config.setAllSheets(List.of());
        config.setReports(List.of());
        var result = service.generate(request(List.of("FSAP")));
        assertThat(result.comparisonWorkbookPath()).isNull();
        assertThat(result.workbookPath()).isRegularFile();
        assertThat(Files.mismatch(result.workbookPath(), result.passResults().get(0).workbookPath())).isEqualTo(-1L);
        assertThat(result.runDirectory().resolve("FSAP/維運月度報表_10月彙總_FSAP_202611020330.xlsx"))
                .isRegularFile();
    }
}
