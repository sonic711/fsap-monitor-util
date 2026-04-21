package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.report.ReportGenerationService;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "generate-report", mixinStandardHelpOptions = true, description = "Execute report SQL and produce Excel/CSV output")
public class GenerateReportCommand implements Runnable {

    @Option(names = "--timestamp", description = "Override output batch timestamp")
    String timestamp;

    @Option(names = "--continue-on-error", description = "Continue processing remaining SQL files when one fails")
    boolean continueOnError;

    private final ReportGenerationService reportGenerationService;

    public GenerateReportCommand(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @Override
    public void run() {
        var result = reportGenerationService.generate(timestamp, continueOnError);
        System.out.printf(
                "Report generation completed: timestamp=%s success=%d failure=%d%n",
                result.timestamp(),
                result.reportResults().stream().filter(ReportGenerationService.ReportFileResult::success).count(),
                result.failures().size()
        );
        System.out.println("Workbook: " + result.workbookPath());
    }
}
