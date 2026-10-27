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
        String historyEndMonth,
        List<String> targetBzkinds
) {

    public static ReportGenerationRequest empty() {
        return new ReportGenerationRequest(null, false, null, null, null, null, null, null, null, null);
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
                trimToNull(historyEndMonth),
                normalizeBzkinds(targetBzkinds)
        );
    }

    public Map<String, String> templateParameters() {
        return templateParameters("ALL");
    }

    /**
     * 指定本輪的業務分類（ALL 表示不篩選）。
     */
    public Map<String, String> templateParameters(String targetBzkind) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("targetBzkind", targetBzkind);
        parameters.put("targetMonth", targetMonth);
        parameters.put("rangeStartDate", rangeStartDate);
        parameters.put("rangeEndDate", rangeEndDate);
        parameters.put("rangeStartTime", rangeStartTime);
        parameters.put("rangeEndTime", rangeEndTime);
        parameters.put("historyStartMonth", historyStartMonth);
        parameters.put("historyEndMonth", historyEndMonth);
        parameters.put("previousTargetMonth", previousTargetMonth());
        parameters.put("previousRangeStartTime", previousRangeStartTime());
        parameters.put("previousRangeEndTime", previousRangeEndTime());
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
        if (targetBzkinds != null && !targetBzkinds.isEmpty()) {
            parts.add("targetBzkinds=" + String.join(",", targetBzkinds));
        }
        if (continueOnError) {
            parts.add("continueOnError=true");
        }
        return parts;
    }

    /**
     * 解析逗號分隔的分類字串（CLI、網頁共用）：null 代表未指定（使用設定預設值），空字串代表只產 ALL。
     */
    public static List<String> parseBzkinds(String value) {
        if (value == null) {
            return null;
        }
        return java.util.Arrays.stream(value.split("[,，]")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    /**
     * null 代表未指定（使用設定預設值）；非 null 時去空白、去空值並去重，保留輸入順序。
     */
    static List<String> normalizeBzkinds(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(ReportGenerationRequest::trimToNull)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
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

    private String previousTargetMonth() {
        return java.time.YearMonth.parse(targetMonth).minusMonths(1).toString();
    }

    private String previousRangeStartTime() {
        return java.time.YearMonth.parse(targetMonth).minusMonths(1).atDay(1).atStartOfDay().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private String previousRangeEndTime() {
        java.time.YearMonth previousMonth = java.time.YearMonth.parse(targetMonth).minusMonths(1);
        return previousMonth.atEndOfMonth().atTime(23, 59).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
