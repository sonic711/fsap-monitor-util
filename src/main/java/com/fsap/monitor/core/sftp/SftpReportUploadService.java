package com.fsap.monitor.core.sftp;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.core.sftp.SftpInputDownloadService.LatestDownloadMetadata;
import com.fsap.monitor.sftp.config.EncryptConfiguration;
import com.fsap.monitor.sftp.transfer.FileTransfer;
import com.fsap.monitor.sftp.transfer.FileTransferUtils;
import com.fsap.monitor.sftp.vo.RemoteInfo;

@Service
/**
 * 將最新產出的月報 Excel 上傳回 SFTP 原始下載檔所在目錄。
 */
public class SftpReportUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SftpReportUploadService.class);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter LOG_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern REPORT_BATCH_DIRECTORY = Pattern.compile("^\\d{12}$");
    private static final String SFTP_LOG_FILE = "sftp_upload.log";

    private final RemoteInfo remoteInfo;
    private final ProjectPathService projectPathService;
    private final SftpInputDownloadService sftpInputDownloadService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SftpReportUploadService(
            RemoteInfo remoteInfo,
            ProjectPathService projectPathService,
            SftpInputDownloadService sftpInputDownloadService,
            ObjectMapper objectMapper
    ) {
        this(remoteInfo, projectPathService, sftpInputDownloadService, objectMapper, Clock.system(DEFAULT_ZONE));
    }

    SftpReportUploadService(
            RemoteInfo remoteInfo,
            ProjectPathService projectPathService,
            SftpInputDownloadService sftpInputDownloadService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.remoteInfo = remoteInfo;
        this.projectPathService = projectPathService;
        this.sftpInputDownloadService = sftpInputDownloadService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public UploadResult uploadReport(UploadRequest request) {
        Path localFile = request.localFile() != null
                ? request.localFile().toAbsolutePath().normalize()
                : latestWorkbook();
        String remoteDirectory = request.remoteDirectory() != null && !request.remoteDirectory().isBlank()
                ? request.remoteDirectory().trim()
                : latestDownloadedRemoteDirectory(request.metadataFile());
        String remoteFilename = localFile.getFileName().toString();
        String remotePath = remoteDirectory.endsWith("/")
                ? remoteDirectory + remoteFilename
                : remoteDirectory + "/" + remoteFilename;

        if (!Files.isRegularFile(localFile)) {
            throw new IllegalArgumentException("Local report file not found: " + localFile);
        }
        if (!remoteFilename.toLowerCase().endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only .xlsx report files can be uploaded: " + localFile);
        }

        logExecution("START localFile=" + localFile
                + ", remoteDirectory=" + remoteDirectory
                + ", overwrite=" + request.overwrite()
                + ", remoteUrl=" + remoteInfo.getUrl()
                + ", username=" + remoteInfo.getUsername());

        try (FileTransfer transfer = FileTransferUtils.newInstance(decryptedRemoteInfo())) {
            logExecution("CONNECT remoteUrl=" + remoteInfo.getUrl() + ", username=" + remoteInfo.getUsername());
            transfer.connection();
            logExecution("CONNECTED remoteUrl=" + remoteInfo.getUrl());
            transfer.cd(remoteDirectory);
            if (!request.overwrite() && !transfer.ls(remoteFilename).isEmpty()) {
                throw new IllegalStateException("Remote report already exists: " + remotePath);
            }
            try (InputStream inputStream = Files.newInputStream(localFile)) {
                transfer.put(remoteFilename, inputStream);
            }
            logExecution("SUCCESS remotePath=" + remotePath + ", localFile=" + localFile);
            return new UploadResult(localFile, remotePath);
        } catch (Exception exception) {
            String message = "Unable to upload report to SFTP: localFile=" + localFile
                    + ", remotePath=" + remotePath
                    + ", cause=" + rootCauseMessage(exception);
            logExecution("FAILED " + message);
            throw new IllegalStateException(message, exception);
        }
    }

    private String latestDownloadedRemoteDirectory(Path metadataFileOverride) {
        Path metadataFile = metadataFileOverride != null
                ? metadataFileOverride.toAbsolutePath().normalize()
                : sftpInputDownloadService.latestDownloadMetadataFile();
        if (!Files.isRegularFile(metadataFile)) {
            throw new IllegalStateException("Latest SFTP download metadata not found: " + metadataFile
                    + ". Run download-input first or pass --remote-dir.");
        }
        try {
            LatestDownloadMetadata metadata = objectMapper.readValue(metadataFile.toFile(), LatestDownloadMetadata.class);
            if (metadata.remoteDirectory() == null || metadata.remoteDirectory().isBlank()) {
                throw new IllegalStateException("remoteDirectory is missing in metadata: " + metadataFile);
            }
            return metadata.remoteDirectory();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read latest SFTP download metadata: " + metadataFile, exception);
        }
    }

    private Path latestWorkbook() {
        Path reportOutputDir = projectPathService.reportOutputDir();
        if (!Files.isDirectory(reportOutputDir)) {
            throw new IllegalStateException("Report output directory not found: " + reportOutputDir);
        }
        try {
            List<Path> batchDirectories;
            try (var stream = Files.list(reportOutputDir)) {
                batchDirectories = stream
                        .filter(Files::isDirectory)
                        .filter(path -> REPORT_BATCH_DIRECTORY.matcher(path.getFileName().toString()).matches())
                        .sorted(Comparator.comparing(this::lastModifiedTime).reversed())
                        .toList();
            }
            for (Path batchDirectory : batchDirectories) {
                try (var stream = Files.list(batchDirectory)) {
                    List<Path> workbooks = stream
                            .filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".xlsx"))
                            .sorted(Comparator.comparing(this::lastModifiedTime).reversed())
                            .toList();
                    if (!workbooks.isEmpty()) {
                        return workbooks.get(0).toAbsolutePath().normalize();
                    }
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to locate latest report workbook under: " + reportOutputDir, exception);
        }
        throw new IllegalStateException("No report workbook found under: " + reportOutputDir);
    }

    private long lastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception exception) {
            return 0L;
        }
    }

    private void logExecution(String message) {
        LOGGER.info(message);
        try {
            Files.createDirectories(projectPathService.logDir());
            Path logFile = projectPathService.logDir().resolve(SFTP_LOG_FILE);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    logFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                writer.write(LocalDateTime.now(clock).format(LOG_DATE_TIME_FORMAT) + " " + message);
                writer.newLine();
            }
        } catch (Exception exception) {
            LOGGER.warn("Unable to write SFTP upload log", exception);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
    }

    private RemoteInfo decryptedRemoteInfo() {
        RemoteInfo copy = new RemoteInfo();
        copy.setUrl(remoteInfo.getUrl());
        copy.setUsername(remoteInfo.getUsername());
        copy.setCode(EncryptConfiguration.decrypt(remoteInfo.getCode()));
        copy.setDefPath(remoteInfo.getDefPath());
        return copy;
    }

    public record UploadRequest(
            Path localFile,
            String remoteDirectory,
            Path metadataFile,
            boolean overwrite
    ) {
    }

    public record UploadResult(
            Path localFile,
            String remotePath
    ) {
    }
}
