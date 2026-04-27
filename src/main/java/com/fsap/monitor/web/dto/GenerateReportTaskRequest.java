package com.fsap.monitor.web.dto;

import com.fsap.monitor.core.report.ReportGenerationRequest;

public record GenerateReportTaskRequest(
        String timestamp,
        boolean continueOnError,
        String targetMonth,
        String rangeStartDate,
        String rangeEndDate,
        String rangeStartTime,
        String rangeEndTime,
        String historyStartMonth,
        String historyEndMonth
) {

    public static GenerateReportTaskRequest empty() {
        return new GenerateReportTaskRequest(null, false, null, null, null, null, null, null, null);
    }

    public ReportGenerationRequest toReportGenerationRequest() {
        return new ReportGenerationRequest(
                timestamp,
                continueOnError,
                targetMonth,
                rangeStartDate,
                rangeEndDate,
                rangeStartTime,
                rangeEndTime,
                historyStartMonth,
                historyEndMonth
        );
    }
}
