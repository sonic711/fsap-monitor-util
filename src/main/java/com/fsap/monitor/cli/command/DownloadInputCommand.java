package com.fsap.monitor.cli.command;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.sftp.SftpInputDownloadService;
import com.fsap.monitor.core.sftp.SftpInputDownloadService.DownloadRequest;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
/**
 * 從 SFTP 備份目錄下載每日交易統計 Excel 到 01_excel_input。
 */
@Command(name = "download-input", mixinStandardHelpOptions = true, description = "Download daily Excel input from SFTP into 01_excel_input")
public class DownloadInputCommand implements Runnable {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @Option(names = "--date", description = "Target Gregorian date in yyyyMMdd. Defaults to today in Asia/Taipei.")
    String date;

    @Option(names = "--filename", description = "Override remote filename. Defaults to FSAP每日交易統計{yyyyMMdd}.xlsx.")
    String filename;

    @Option(names = "--remote-root", description = "Remote backup root directory. Defaults to /FSAP/FILE_BCKP.")
    String remoteRoot;

    @Option(names = "--local-dir", description = "Override local target directory. Defaults to fsap.paths.input-dir.")
    Path localDirectory;

    @Option(names = "--overwrite", description = "Overwrite local file if it already exists.")
    boolean overwrite;

    private final SftpInputDownloadService sftpInputDownloadService;

    public DownloadInputCommand(SftpInputDownloadService sftpInputDownloadService) {
        this.sftpInputDownloadService = sftpInputDownloadService;
    }

    @Override
    public void run() {
        LocalDate targetDate = date == null || date.isBlank() ? null : LocalDate.parse(date, DATE_FORMAT);
        var result = sftpInputDownloadService.downloadDailyInput(new DownloadRequest(
                targetDate,
                filename,
                remoteRoot,
                localDirectory,
                overwrite
        ));
        System.out.println("SFTP input download completed");
        System.out.println("Remote: " + result.remotePath());
        System.out.println("Local: " + result.localPath());
    }
}
