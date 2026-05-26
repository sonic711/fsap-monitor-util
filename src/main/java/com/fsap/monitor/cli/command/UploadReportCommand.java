package com.fsap.monitor.cli.command;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.sftp.SftpReportUploadService;
import com.fsap.monitor.core.sftp.SftpReportUploadService.UploadRequest;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
/**
 * 將月報 Excel 上傳回 SFTP。
 */
@Command(name = "upload-report", mixinStandardHelpOptions = true, description = "Upload generated report workbook to SFTP")
public class UploadReportCommand implements Runnable {

    @Option(names = "--local-file", description = "Local .xlsx report to upload. Defaults to the latest report workbook.")
    Path localFile;

    @Option(names = "--remote-dir", description = "Remote target directory. Defaults to the directory recorded by the latest download-input run.")
    String remoteDirectory;

    @Option(names = "--metadata-file", description = "Override latest SFTP download metadata file.")
    Path metadataFile;

    @Option(names = "--overwrite", description = "Overwrite remote report if it already exists.")
    boolean overwrite;

    private final SftpReportUploadService sftpReportUploadService;

    public UploadReportCommand(SftpReportUploadService sftpReportUploadService) {
        this.sftpReportUploadService = sftpReportUploadService;
    }

    @Override
    public void run() {
        var result = sftpReportUploadService.uploadReport(new UploadRequest(
                localFile,
                remoteDirectory,
                metadataFile,
                overwrite
        ));
        System.out.println("SFTP report upload completed");
        System.out.println("Local: " + result.localFile());
        System.out.println("Remote: " + result.remotePath());
    }
}
