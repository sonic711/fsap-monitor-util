package com.fsap.monitor.core.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.fsap.monitor.core.service.ProjectPathService;
import com.fsap.monitor.infra.config.FsapProperties;

@Service
public class ReportParameterDefaultsService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> DATE_TIME_INPUT_FORMATS = List.of(
            DATE_TIME_FORMAT,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    );
    private static final Pattern SOURCE_LAKE_DATE_PATTERN = Pattern.compile("-(\\d{8})\\.jsonl\\.gz$");

    private final ProjectPathService projectPathService;
    private final FsapProperties properties;

    public ReportParameterDefaultsService(ProjectPathService projectPathService, FsapProperties properties) {
        this.projectPathService = projectPathService;
        this.properties = properties;
    }

    public ReportGenerationRequest defaultRequest() {
        return resolve(ReportGenerationRequest.empty());
    }

    public ReportGenerationRequest resolve(ReportGenerationRequest request) {
        ReportGenerationRequest normalized = request == null ? ReportGenerationRequest.empty() : request.normalize();

        YearMonth detectedMonth = detectReferenceMonth();
        YearMonth targetMonth = parseYearMonth(
                firstNonBlank(
                        normalized.targetMonth(),
                        monthFromDate(normalized.rangeEndDate()),
                        monthFromDate(normalized.rangeStartDate()),
                        monthFromDateTime(normalized.rangeEndTime()),
                        monthFromDateTime(normalized.rangeStartTime()),
                        detectedMonth.format(MONTH_FORMAT)
                ),
                "targetMonth"
        );

        LocalDate rangeStartDate = normalized.rangeStartDate() == null
                ? targetMonth.atDay(1)
                : parseDate(normalized.rangeStartDate(), "rangeStartDate");
        LocalDate rangeEndDate = normalized.rangeEndDate() == null
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
                ? historyEndMonth.minusMonths(7)
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

    private YearMonth detectReferenceMonth() {
        return Stream.of(scanLatestInputDate(), scanLatestSourceLakeDate(), LocalDate.now())
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(YearMonth::from)
                .orElse(YearMonth.now());
    }

    private LocalDate scanLatestInputDate() {
        Path inputDir = projectPathService.inputDir();
        if (!Files.isDirectory(inputDir)) {
            return null;
        }

        Pattern filenamePattern = Pattern.compile(properties.getIngest().getFilenamePattern());
        try (Stream<Path> stream = Files.list(inputDir)) {
            return stream
                    .map(path -> extractInputDate(path.getFileName().toString(), filenamePattern))
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private LocalDate scanLatestSourceLakeDate() {
        Path sourceLakeDir = projectPathService.sourceLakeDir();
        if (!Files.isDirectory(sourceLakeDir)) {
            return null;
        }

        try (Stream<Path> stream = Files.walk(sourceLakeDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> extractSourceLakeDate(path.getFileName().toString()))
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        } catch (Exception exception) {
            return null;
        }
    }

    private LocalDate extractInputDate(String filename, Pattern filenamePattern) {
        Matcher matcher = filenamePattern.matcher(filename);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private LocalDate extractSourceLakeDate(String filename) {
        Matcher matcher = SOURCE_LAKE_DATE_PATTERN.matcher(filename);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            return null;
        }
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
