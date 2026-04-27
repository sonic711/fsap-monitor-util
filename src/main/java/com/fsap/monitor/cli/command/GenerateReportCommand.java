package com.fsap.monitor.cli.command;

import org.springframework.stereotype.Component;

import com.fsap.monitor.core.report.ReportGenerationRequest;
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

    @Option(names = "--target-month", description = "Target reporting month in YYYY-MM format")
    String targetMonth;

    @Option(names = "--range-start-date", description = "Daily report start date in YYYY-MM-DD format")
    String rangeStartDate;

    @Option(names = "--range-end-date", description = "Daily report end date in YYYY-MM-DD format")
    String rangeEndDate;

    @Option(names = "--range-start-time", description = "Detailed report start time in YYYY-MM-DD HH:mm format")
    String rangeStartTime;

    @Option(names = "--range-end-time", description = "Detailed report end time in YYYY-MM-DD HH:mm format")
    String rangeEndTime;

    @Option(names = "--history-start-month", description = "Historical range start month in YYYY-MM format")
    String historyStartMonth;

    @Option(names = "--history-end-month", description = "Historical range end month in YYYY-MM format")
    String historyEndMonth;

    private final ReportGenerationService reportGenerationService;

    public GenerateReportCommand(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @Override
    public void run() {
        var result = reportGenerationService.generate(new ReportGenerationRequest(
                timestamp,
                continueOnError,
                targetMonth,
                rangeStartDate,
                rangeEndDate,
                rangeStartTime,
                rangeEndTime,
                historyStartMonth,
                historyEndMonth
        ));
        System.out.printf(
                "Report generation completed: timestamp=%s success=%d failure=%d%n",
                result.timestamp(),
                result.reportResults().stream().filter(ReportGenerationService.ReportFileResult::success).count(),
                result.failures().size()
        );
        System.out.println("Workbook: " + result.workbookPath());
        System.out.println("Parameters: " + result.parametersFile());
    }
}
