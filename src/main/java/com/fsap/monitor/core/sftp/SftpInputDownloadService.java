package com.fsap.monitor.core.sftp;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.sftp.config.EncryptConfiguration;
import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.transfer.FileTransfer;
import com.fsap.monitor.sftp.transfer.FileTransferUtils;
import com.fsap.monitor.sftp.vo.RemoteInfo;

@Service
/**
 * 從 SFTP 備份目錄抓取每日交易統計 Excel，放到 ingest input 目錄。
 */
public class SftpInputDownloadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SftpInputDownloadService.class);

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter LOG_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String DEFAULT_REMOTE_ROOT = "/FSAP/FILE_BCKP";
    private static final String DAILY_FILE_PREFIX = "FSAP每日交易統計";
    private static final String DAILY_FILE_EXTENSION = ".xlsx";
    private static final String SFTP_LOG_FILE = "sftp_download.log";
    private static final Pattern ROC_BACKUP_DIRECTORY = Pattern.compile("^0\\d{7}$");

    private final RemoteInfo remoteInfo;
    private final ProjectPathService projectPathService;
    private final Clock clock;

    @Autowired
    public SftpInputDownloadService(RemoteInfo remoteInfo, ProjectPathService projectPathService) {
        this(remoteInfo, projectPathService, Clock.system(DEFAULT_ZONE));
    }

    SftpInputDownloadService(RemoteInfo remoteInfo, ProjectPathService projectPathService, Clock clock) {
        this.remoteInfo = remoteInfo;
        this.projectPathService = projectPathService;
        this.clock = clock;
    }

    public DownloadResult downloadDailyInput(DownloadRequest request) {
        LocalDate targetDate = request.targetDate() != null ? request.targetDate() : LocalDate.now(clock);
        String filename = request.filename() != null && !request.filename().isBlank()
                ? request.filename().trim()
                : DAILY_FILE_PREFIX + targetDate.format(FILE_DATE_FORMAT) + DAILY_FILE_EXTENSION;
        String remoteRoot = request.remoteRoot() != null && !request.remoteRoot().isBlank()
                ? request.remoteRoot().trim()
                : DEFAULT_REMOTE_ROOT;
        Path localDirectory = request.localDirectory() != null
                ? request.localDirectory().toAbsolutePath().normalize()
                : projectPathService.inputDir();
        Path localFile = localDirectory.resolve(filename).normalize();

        if (!localFile.startsWith(localDirectory)) {
            String message = "Invalid target filename: " + filename;
            logExecution("FAILED " + message);
            throw new IllegalArgumentException(message);
        }

        logExecution("START filename=" + filename
                + ", remoteRoot=" + remoteRoot
                + ", localDirectory=" + localDirectory
                + ", overwrite=" + request.overwrite()
                + ", remoteUrl=" + remoteInfo.getUrl()
                + ", username=" + remoteInfo.getUsername());

        try {
            Files.createDirectories(localDirectory);
            if (Files.exists(localFile) && !request.overwrite()) {
                throw new IllegalStateException("Local file already exists: " + localFile);
            }

            try (FileTransfer transfer = FileTransferUtils.newInstance(decryptedRemoteInfo())) {
                logExecution("CONNECT remoteUrl=" + remoteInfo.getUrl() + ", username=" + remoteInfo.getUsername());
                transfer.connection();
                logExecution("CONNECTED remoteUrl=" + remoteInfo.getUrl());
                transfer.cd(remoteRoot);
                List<FileEntry> directories = sortedBackupDirectories(transfer.ls());
                logExecution("SCAN remoteRoot=" + remoteRoot + ", candidateDirectories=" + directories.size());
                for (FileEntry directory : directories) {
                    String directoryName = directory.getFileName();
                    transfer.cd(remoteRoot + "/" + directoryName);
                    if (transfer.ls(filename).isEmpty()) {
                        continue;
                    }

                    String remotePath = remoteRoot + "/" + directoryName + "/" + filename;
                    logExecution("MATCH remotePath=" + remotePath);
                    Path tempFile = localDirectory.resolve(filename + ".downloading").normalize();
                    try (OutputStream outputStream = Files.newOutputStream(tempFile)) {
                        transfer.get(filename, outputStream);
                    }
                    Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING);
                    logExecution("SUCCESS remotePath=" + remotePath + ", localPath=" + localFile);
                    return new DownloadResult(filename, remotePath, localFile, directoryName);
                }
            }
        } catch (Exception exception) {
            String message = "Unable to download input file from SFTP: filename=" + filename
                    + ", remoteRoot=" + remoteRoot
                    + ", localDirectory=" + localDirectory
                    + ", cause=" + rootCauseMessage(exception);
            logExecution("FAILED " + message);
            throw new IllegalStateException(message, exception);
        }

        String message = "Remote file not found under " + remoteRoot + ": " + filename;
        logExecution("FAILED " + message);
        throw new IllegalStateException(message);
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
            LOGGER.warn("Unable to write SFTP download log", exception);
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

    private List<FileEntry> sortedBackupDirectories(List<FileEntry> entries) {
        return entries.stream()
                .filter(entry -> !entry.isFile())
                .filter(entry -> !".".equals(entry.getFileName()))
                .filter(entry -> !"..".equals(entry.getFileName()))
                .sorted(Comparator
                        .comparing((FileEntry entry) -> ROC_BACKUP_DIRECTORY.matcher(entry.getFileName()).matches()).reversed()
                        .thenComparing(FileEntry::getFileName, Comparator.reverseOrder()))
                .toList();
    }

    private RemoteInfo decryptedRemoteInfo() {
        RemoteInfo copy = new RemoteInfo();
        copy.setUrl(remoteInfo.getUrl());
        copy.setUsername(remoteInfo.getUsername());
        copy.setCode(EncryptConfiguration.decrypt(remoteInfo.getCode()));
        copy.setDefPath(remoteInfo.getDefPath());
        return copy;
    }

    public record DownloadRequest(
            LocalDate targetDate,
            String filename,
            String remoteRoot,
            Path localDirectory,
            boolean overwrite
    ) {
    }

    public record DownloadResult(
            String filename,
            String remotePath,
            Path localPath,
            String matchedDirectory
    ) {
    }
}
