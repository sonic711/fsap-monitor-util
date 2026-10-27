package com.fsap.monitor.core.sftp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.sftp.SftpInputDownloadService.LatestDownloadMetadata;
import com.fsap.monitor.core.sftp.SftpReportUploadService.UploadRequest;
import com.fsap.monitor.infra.config.FsapProperties;
import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.transfer.FileTransfer;
import com.fsap.monitor.sftp.transfer.FileTransferUtils;
import com.fsap.monitor.sftp.vo.RemoteInfo;

class SftpReportUploadServiceTest {

    private static final String FILENAME = "維運月度報表_10月彙總_202611020330.xlsx";
    private static final String REMOTE_DIRECTORY = "/FSAP/FILE_BCKP/01151101";

    @TempDir
    Path baseDirectory;

    private ProjectPathService paths;
    private Path monthly;
    private Path metadataFile;
    private FileTransfer transfer;
    private SftpReportUploadService service;

    @BeforeEach
    void prepareMonthlyWorkbookAndDownloadMetadata() throws Exception {
        var properties = new FsapProperties();
        properties.getPaths().setBaseDir(baseDirectory.toString());
        paths = new ProjectPathService(properties);
        Path batch = paths.reportOutputDir().resolve("202611020330");
        Files.createDirectories(batch.resolve("SUM"));
        Files.createDirectories(batch.resolve("FSAP"));
        monthly = batch.resolve(FILENAME);
        try (var workbook = new XSSFWorkbook(); var output = Files.newOutputStream(monthly)) {
            workbook.createSheet("總覽-交易量統計").createRow(0).createCell(0).setCellValue("全系統（ALL）");
            workbook.createSheet("FSAP-當月每日").createRow(0).createCell(0).setCellValue("FSAP");
            workbook.write(output);
        }
        Files.writeString(batch.resolve("SUM").resolve(FILENAME), "ALL-only workbook must not be selected");
        Files.writeString(batch.resolve("FSAP/維運月度報表_10月彙總_FSAP_202611020330.xlsx"), "FSAP-only workbook");
        Path olderBatch = paths.reportOutputDir().resolve("202610020330");
        Files.createDirectories(olderBatch);
        Files.writeString(olderBatch.resolve("維運月度報表_09月彙總_202610020330.xlsx"), "Older layout workbook");
        Files.setLastModifiedTime(olderBatch, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(batch, FileTime.fromMillis(2000));

        var mapper = new ObjectMapper();
        Files.createDirectories(paths.logDir());
        metadataFile = paths.logDir().resolve("latest-sftp-download.json");
        mapper.writeValue(metadataFile.toFile(), new LatestDownloadMetadata("daily.xlsx",
                REMOTE_DIRECTORY + "/daily.xlsx", REMOTE_DIRECTORY, "daily.xlsx", "01151101", "2026-11-02 03:00:00"));
        var downloads = mock(SftpInputDownloadService.class);
        when(downloads.latestDownloadMetadataFile()).thenReturn(metadataFile);
        var remote = new RemoteInfo();
        remote.setUrl("sftp://127.0.0.1:22");
        remote.setUsername("test-user");
        transfer = mock(FileTransfer.class);
        when(transfer.ls(FILENAME)).thenReturn(List.of());
        service = new SftpReportUploadService(remote, paths, downloads, mapper);
    }

    @Test
    void uploadsNewMonthlyWorkbookWithUnchangedChineseFilenameAndExactBytes() throws Exception {
        var received = new ByteArrayOutputStream();
        doAnswer(invocation -> {
            InputStream input = invocation.getArgument(1);
            input.transferTo(received);
            return null;
        }).when(transfer).put(eq(FILENAME), any(InputStream.class));

        try (var factory = mockStatic(FileTransferUtils.class)) {
            factory.when(() -> FileTransferUtils.newInstance(any())).thenReturn(transfer);
            var result = service.uploadReport(new UploadRequest(null, null, null, false));

            assertThat(result.localFile()).isEqualTo(monthly);
            assertThat(result.remotePath()).isEqualTo(REMOTE_DIRECTORY + "/" + FILENAME);
            assertThat(received.toByteArray()).isEqualTo(Files.readAllBytes(monthly));
            verify(transfer).connection();
            verify(transfer).cd(REMOTE_DIRECTORY);
            verify(transfer).ls(FILENAME);
            verify(transfer).close();
            assertThat(Files.readString(paths.logDir().resolve("sftp_upload.log")))
                    .contains("SUCCESS remotePath=" + result.remotePath());
        }
    }

    @Test
    void existingRemoteReportIsNotOverwrittenByDefault() throws Exception {
        when(transfer.ls(FILENAME)).thenReturn(List.of(mock(FileEntry.class)));
        try (var factory = mockStatic(FileTransferUtils.class)) {
            factory.when(() -> FileTransferUtils.newInstance(any())).thenReturn(transfer);
            assertThatThrownBy(() -> service.uploadReport(new UploadRequest(null, null, null, false)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Remote report already exists");
            verify(transfer, never()).put(any(), any());
            verify(transfer).close();
        }
    }

    @Test
    void overwriteAndExplicitRemoteDirectoryRemainSupported() throws Exception {
        try (var factory = mockStatic(FileTransferUtils.class)) {
            factory.when(() -> FileTransferUtils.newInstance(any())).thenReturn(transfer);
            var result = service.uploadReport(new UploadRequest(monthly, "/manual/monthly/", null, true));
            assertThat(result.remotePath()).isEqualTo("/manual/monthly/" + FILENAME);
            verify(transfer).cd("/manual/monthly/");
            verify(transfer, never()).ls(any(String.class));
            verify(transfer).put(eq(FILENAME), any(InputStream.class));
        }
    }

    @Test
    void missingDownloadMetadataStopsBeforeConnecting() throws Exception {
        Files.delete(metadataFile);
        try (var factory = mockStatic(FileTransferUtils.class)) {
            assertThatThrownBy(() -> service.uploadReport(new UploadRequest(null, null, null, false)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Run download-input first or pass --remote-dir");
            factory.verifyNoInteractions();
            verifyNoInteractions(transfer);
        }
    }
}
