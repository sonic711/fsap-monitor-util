package com.fsap.monitor.core.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ReportGenerationRequest(
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

    public static ReportGenerationRequest empty() {
        return new ReportGenerationRequest(null, false, null, null, null, null, null, null, null);
    }

    public ReportGenerationRequest normalize() {
        return new ReportGenerationRequest(
                trimToNull(timestamp),
                continueOnError,
                trimToNull(targetMonth),
                trimToNull(rangeStartDate),
                trimToNull(rangeEndDate),
                trimToNull(rangeStartTime),
                trimToNull(rangeEndTime),
                trimToNull(historyStartMonth),
                trimToNull(historyEndMonth)
        );
    }

    public Map<String, String> templateParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("targetMonth", targetMonth);
        parameters.put("rangeStartDate", rangeStartDate);
        parameters.put("rangeEndDate", rangeEndDate);
        parameters.put("rangeStartTime", rangeStartTime);
        parameters.put("rangeEndTime", rangeEndTime);
        parameters.put("historyStartMonth", historyStartMonth);
        parameters.put("historyEndMonth", historyEndMonth);
        return parameters;
    }

    public List<String> summaryParts() {
        List<String> parts = new ArrayList<>();
        if (timestamp != null) {
            parts.add("timestamp=" + timestamp);
        }
        if (targetMonth != null) {
            parts.add("targetMonth=" + targetMonth);
        }
        if (rangeStartDate != null || rangeEndDate != null) {
            parts.add("dateRange=" + blankSafe(rangeStartDate) + "~" + blankSafe(rangeEndDate));
        }
        if (rangeStartTime != null || rangeEndTime != null) {
            parts.add("timeRange=" + blankSafe(rangeStartTime) + "~" + blankSafe(rangeEndTime));
        }
        if (historyStartMonth != null || historyEndMonth != null) {
            parts.add("historyMonthRange=" + blankSafe(historyStartMonth) + "~" + blankSafe(historyEndMonth));
        }
        if (continueOnError) {
            parts.add("continueOnError=true");
        }
        return parts;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blankSafe(String value) {
        return value == null ? "" : value;
    }
}
