package com.fsap.monitor.core.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
/**
 * 將部分填寫的報表參數請求補齊成完整可執行的參數集。
 *
 * <p>UI 刻意只暴露較少的參數欄位，但 SQL 模板仍需要完整日期與時間區間。
 * 這個 service 會負責推導缺少的細節，讓 UI 與 CLI 可以共用同一套報表執行引擎。
 */
public class ReportParameterDefaultsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final YearMonth DEFAULT_HISTORY_START_MONTH = YearMonth.of(2025, 9);
    private static final List<DateTimeFormatter> DATE_TIME_INPUT_FORMATS = List.of(
            DATE_TIME_FORMAT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    );
    /**
     * 回傳與 UI 初始畫面相同的預設報表參數。
     */
    public ReportGenerationRequest defaultRequest() {
        return resolve(ReportGenerationRequest.empty());
    }

    /**
     * 依據最小輸入集合推導所有缺漏欄位。
     */
    public ReportGenerationRequest resolve(ReportGenerationRequest request) {
        ReportGenerationRequest normalized = request == null ? ReportGenerationRequest.empty() : request.normalize();

        YearMonth defaultTargetMonth = defaultTargetMonth();
        YearMonth targetMonth = parseYearMonth(
                firstNonBlank(
                        normalized.targetMonth(),
                        monthFromDate(normalized.rangeEndDate()),
                        monthFromDate(normalized.rangeStartDate()),
                        monthFromDateTime(normalized.rangeEndTime()),
                        monthFromDateTime(normalized.rangeStartTime()),
                        defaultTargetMonth.format(MONTH_FORMAT)
                ),
                "targetMonth"
        );

        LocalDate rangeStartDate = normalized.rangeStartDate() == null
                ? targetMonth.atDay(1)
                : parseDate(normalized.rangeStartDate(), "rangeStartDate");
        LocalDate rangeEndDate = normalized.rangeEndDate() == null
                // atEndOfMonth() 會正確處理 28/29/30/31 天與閏年，不需手動判斷月底天數。
                ? targetMonth.atEndOfMonth()
                : parseDate(normalized.rangeEndDate(), "rangeEndDate");
        if (rangeEndDate.isBefore(rangeStartDate)) {
            throw new IllegalStateException("rangeEndDate must be on or after rangeStartDate");
        }

        LocalDateTime rangeStartTime = normalized.rangeStartTime() == null
                ? rangeStartDate.atStartOfDay()
                : parseDateTime(normalized.rangeStartTime(), "rangeStartTime");
        LocalDateTime rangeEndTime = normalized.rangeEndTime() == null
                ? rangeEndDate.atTime(23, 59)
                : parseDateTime(normalized.rangeEndTime(), "rangeEndTime");
        if (rangeEndTime.isBefore(rangeStartTime)) {
            throw new IllegalStateException("rangeEndTime must be on or after rangeStartTime");
        }

        YearMonth historyEndMonth = normalized.historyEndMonth() == null
                ? targetMonth
                : parseYearMonth(normalized.historyEndMonth(), "historyEndMonth");
        YearMonth historyStartMonth = normalized.historyStartMonth() == null
                ? DEFAULT_HISTORY_START_MONTH
                : parseYearMonth(normalized.historyStartMonth(), "historyStartMonth");
        if (historyEndMonth.isBefore(historyStartMonth)) {
            throw new IllegalStateException("historyEndMonth must be on or after historyStartMonth");
        }

        return new ReportGenerationRequest(
                normalized.timestamp(),
                normalized.continueOnError(),
                targetMonth.format(MONTH_FORMAT),
                rangeStartDate.toString(),
                rangeEndDate.toString(),
                rangeStartTime.format(DATE_TIME_FORMAT),
                rangeEndTime.format(DATE_TIME_FORMAT),
                historyStartMonth.format(MONTH_FORMAT),
                historyEndMonth.format(MONTH_FORMAT)
        );
    }

    private YearMonth defaultTargetMonth() {
        // 預設報表月份固定使用「本月的上個月」，避免當月資料尚未完整時，
        // UI 一開啟就直接落在仍在累積中的月份。
        return YearMonth.from(LocalDate.now().minusMonths(1));
    }

    private YearMonth parseYearMonth(String value, String fieldName) {
        try {
            return YearMonth.parse(value, MONTH_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(fieldName + " must use YYYY-MM format", exception);
        }
    }

    private LocalDate parseDate(String value, String fieldName) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException(fieldName + " must use YYYY-MM-DD format", exception);
        }
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        for (DateTimeFormatter formatter : DATE_TIME_INPUT_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalStateException(fieldName + " must use YYYY-MM-DD HH:mm format");
    }

    private String monthFromDate(String value) {
        if (value == null) {
            return null;
        }
        return parseDate(value, "derivedDate").format(MONTH_FORMAT);
    }

    private String monthFromDateTime(String value) {
        if (value == null) {
            return null;
        }
        return YearMonth.from(parseDateTime(value, "derivedDateTime")).format(MONTH_FORMAT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
