package com.fsap.monitor.core.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.sftp.vo.RemoteInfo;

class SftpReportSelectionTest {

    @TempDir
    Path baseDirectory;

    @Test
    void automaticUploadSelectsRootMonthlyWorkbookNotSumOrCategoryWorkbooks() throws Exception {
        var properties = new FsapProperties();
        properties.getPaths().setBaseDir(baseDirectory.toString());
        var paths = new ProjectPathService(properties);
        Path batch = paths.reportOutputDir().resolve("202611020330");
        Files.createDirectories(batch.resolve("SUM"));
        Files.createDirectories(batch.resolve("FSAP"));
        String filename = "維運月度報表_10月彙總_202611020330.xlsx";
        Path monthly = Files.createFile(batch.resolve(filename));
        Files.createFile(batch.resolve("SUM").resolve(filename));
        Files.createFile(batch.resolve("FSAP/維運月度報表_10月彙總_FSAP_202611020330.xlsx"));
        var service = new SftpReportUploadService(new RemoteInfo(), paths, null, new ObjectMapper());

        Path selected = ReflectionTestUtils.invokeMethod(service, "latestWorkbook");
        assertThat(selected).isEqualTo(monthly);
    }
}
