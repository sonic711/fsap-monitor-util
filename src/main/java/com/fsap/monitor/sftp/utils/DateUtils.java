package com.fsap.monitor.sftp.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Pattern;

public final class DateUtils {

	private DateUtils() {
		throw new IllegalStateException("Utility class");
	}

	public interface TimeFormatPatten {
		/** 民國年判斷 eeMMdd 或 eeeMMdd 或 yyyMMdd */
		Pattern MINGUO_PATTEN = Pattern.compile("((([eE]{2,4})|([yY]{3})){1}[-|////|年]{0,1}){1}([M]{2}[-|////|月]{0,1})?([dD]{2}[日]{0,1})?");
		/** 簡易判斷是否有時間格式 */
		Pattern TIME_PATTEN = Pattern.compile("([eE]{2,3})|([yY]{2,4})|([M]{2})|([dD]{2})|([hh]{2})|([HH]{2})|([ss]{2})|([SSS]{2})");
	}

	public static final String MINGUO_DATE_PATTERN = "yyyMMdd";
	public static final String DATE_PATTERN = "yyyy/MM/dd";
	public static final String DATE_DASH = "yyyy-MM-dd";
	public static final String DATE_NOTHING = "yyyyMMdd";
	public static final String TIMESTAMP_PATTERN = "yyyy/MM/dd HH:mm:ss";
	public static final String TIMESTAMP_DASH = "yyyy-MM-dd HH:mm:ss";
	public static final String TIMESTAMP_NOTHING = "yyyyMMddHHmmss";
	public static final String TIMESTAMP_NOTHING_MS = "yyyyMMddHHmmssSSS";
	public static final String TIMESTAMP_NOTHING_MS_DASH = "yyyyMMdd-HHmmss-SSS";
	public static final String DEFAULT_INPUT_PATTERN = "yyyy/MM/dd";
	public static final String DEFAULT_OUTPUT_PATTERN = "yyyy/MM/dd";
	public static final String TIME_PLAIN = "HHmmss";
	public static final String TIME_COLON = "HH:mm:ss";
	public static final String TIME_HOUR_MINUTE = "HH:mm";
	public static final String DATE_MONTHLY = "yyyy/MM";

	/**
	 * 回傳當日日期yyyy/MM/dd字串
	 *
	 * @return
	 */
	public static String now() {
		return now(DATE_PATTERN);
	}

	/**
	 * 依傳入格式回傳當日日期字串
	 *
	 * @param format
	 * @return
	 */
	public static String now(String format) {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
	}

	/**
	 * 檢核yyyyMMdd 是否符合西元日期格式
	 *
	 * @param date
	 * @return
	 */
	public static Boolean checkLocalDate(String date) {

		Boolean status = Boolean.TRUE;
		try {
			int inputDate = Integer.parseInt(date.substring(6, 8));
			LocalDate localdate = DateUtils.strToLocalDate(date);
			int dayOfMonth = localdate.getDayOfMonth();
			if (inputDate > dayOfMonth) {
				status = Boolean.FALSE;
			}
		} catch (Exception e) {
			status = Boolean.FALSE;
		}

		return status;
	}

	/**
	 * 將String轉換為LocalDate DATE_PATTERN
	 */
	public static LocalDate stringToLocalDate(String date) {

		return null == date ? null : LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_PATTERN));
	}

	/**
	 * 將Localdate轉為yyyy/MM/dd字串
	 *
	 * @param localDate
	 * @return
	 */
	public static String dateToString(LocalDate localDate) {

		return null == localDate ? "" : localDate.format(DateTimeFormatter.ofPattern(DEFAULT_OUTPUT_PATTERN));
	}

	/**
	 * 將localDate依輸入格式回傳字串
	 *
	 * @param localDate
	 * @param format
	 * @return
	 */
	public static String dateToString(LocalDate localDate, String format) {

		return null == localDate ? "" : localDate.format(DateTimeFormatter.ofPattern(format));
	}

	/**
	 * 將localDateTime轉為yyyy/MM/dd字串
	 *
	 * @param localDateTime
	 * @return
	 */
	public static String dateToString(LocalDateTime localDateTime) {

		return null == localDateTime ? "" : localDateTime.format(DateTimeFormatter.ofPattern(DEFAULT_OUTPUT_PATTERN));
	}

	/**
	 * 將localDateTime依輸入格式回傳字串
	 *
	 * @param localDateTime
	 * @param format
	 * @return
	 */
	public static String dateToString(LocalDateTime localDateTime, String format) {

		return null == localDateTime ? "" : localDateTime.format(DateTimeFormatter.ofPattern(format));
	}

	/**
	 * 依傳入日期回傳該日yyyyMMdd 00:00:00 及 23:59:59字串
	 *
	 * @param formatter
	 * @return
	 */
	public static String[] getDayTimeRange(LocalDate date) {
		return getDayTimeRange(date, date);
	}

	/**
	 * 依傳入日期回傳起日yyyyMMdd 00:00:00 及 迄日 23:59:59字串
	 *
	 * @param formatter
	 * @return
	 */
	public static String[] getDayTimeRange(String dateStart, String dateEnd) {
		return getDayTimeRange(LocalDate.parse(dateStart.replace("/", "-")), LocalDate.parse(dateEnd.replace("/", "-")));
	}

	/**
	 * 依傳入日期回傳起日yyyyMMdd 00:00:00 及 迄日 23:59:59字串
	 *
	 * @param formatter
	 * @return
	 */
	public static String[] getDayTimeRange(LocalDate start, LocalDate end) {
		String[] result = new String[2];
		result[0] = LocalDateTime.of(start, LocalTime.MIN).format(DateTimeFormatter.ofPattern(TIMESTAMP_DASH));
		result[1] = LocalDateTime.of(end, LocalTime.MAX).format(DateTimeFormatter.ofPattern(TIMESTAMP_DASH));
		return result;
	}

	/**
	 * LocalDate 轉民國年yyy/MM/dd回傳
	 *
	 * @param date
	 * @return
	 */
	public static String toMinguo(LocalDate date) {

		String[] parts = dateToString(date).split("/");
		return String.valueOf((Integer.valueOf(parts[0]) - 1911)) + "/" + parts[1] + "/" + parts[2];
	}

	public static String timeFormate(LocalTime time, String format) {

		return time.format(DateTimeFormatter.ofPattern(format));
	}

	/**
	 * (String)yyyyMMdd轉yyyy-MM-dd(LocalDate)
	 *
	 * @param date
	 * @return
	 */
	public static LocalDate strToLocalDate(String date) {

		return LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_NOTHING));
	}

	/**
	 * 日期字串依格式轉換localDateTime
	 *
	 * @param date
	 * @param format
	 * @return
	 */
	public static LocalDateTime strToLocalDateTime(String date, String format) {

		return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(format));
	}

	/**
	 * /** (String)yyyy-MM-dd HH:mm:ss 轉yyyy-MM-dd HH:mm:ss(LocalDateTime)
	 *
	 * @param date
	 * @return
	 */
	public static LocalDateTime strToLocalDateTime(String date) {

		return LocalDateTime.parse(date, DateTimeFormatter.ofPattern(TIMESTAMP_NOTHING));
	}

	/**
	 * (String)民國年yyyMMdd轉西元
	 *
	 * @param date
	 * @return
	 */
	public static String minguoToAD(String date, String format) {

		LocalDate adLocalDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(MINGUO_DATE_PATTERN)).plusYears(1911);
		return adLocalDate.format(DateTimeFormatter.ofPattern(format));
	}

	/**
	 * (String)yyyyMMdd轉yyyy-MM-dd(LocalDate)
	 *
	 * @param date
	 * @return
	 */
	public static LocalDate strToLocalDate(String date, String format) {

		return LocalDate.parse(date, DateTimeFormatter.ofPattern(format));
	}

	/**
	 * 字串時間轉換為 LocalDate<BR>
	 *
	 * @param format
	 * @param source
	 * @return
	 */
	public static LocalDate parseToLocalDate(DateTimeFormatter format, String source) {

		return LocalDate.from(format.parse(source));
	}

	/**
	 * 字串時間轉換為 LocalDateTime<BR>
	 *
	 * @param format
	 * @param source
	 * @return
	 */
	public static LocalDateTime parseToLocalDateTime(DateTimeFormatter format, String source) {

		return LocalDateTime.from(format.parse(source));
	}

	/**
	 * @param time
	 * @return
	 */
	public static Date toDate(LocalDate time) {

		return Optional.ofNullable(time).map(e -> Date.from(time.atStartOfDay(ZoneId.systemDefault()).toInstant())).orElse(null);
	}

	/**
	 * @param time
	 * @return
	 */
	public static Date toDate(LocalDateTime time) {

		return Optional.ofNullable(time).map(e -> Date.from(time.atZone(ZoneId.systemDefault()).toInstant())).orElse(null);
	}

	/**
	 * 移除時間 FORMAT，以預設DateTimeFormatter.BASIC_ISO_DATE 轉換<BR>
	 * 不驗證移除非數字後，是否符合為BASIC_ISO_DATE格式<BR>
	 * 不驗證參數是否為空值<BR>
	 *
	 * @param date
	 * @return
	 */
	public static LocalDate parseToLocalDate(String date) {

		String tempDate = date.replaceAll("[^\\d]", "");
		return LocalDate.parse(tempDate, DateTimeFormatter.BASIC_ISO_DATE);
	}

	/**
	 * Calendar To LocalDateTime
	 *
	 * @param calendar
	 * @return
	 */
	public static final LocalDateTime toLocalDateTime(Calendar calendar) {

		if (Objects.isNull(calendar)) {
			return null;
		}
		return LocalDateTime.ofInstant(calendar.toInstant(), ZoneId.systemDefault());
	}

	/**
	 * Timestamp(Long) To LocalDateTime
	 *
	 * @param timestamp
	 * @return
	 */
	public static final LocalDateTime toLocalDateTime(Long timestamp) {

		if (Objects.isNull(timestamp)) {
			return null;
		}
		return LocalDateTime.ofInstant(Instant.ofEpochSecond(timestamp), TimeZone.getDefault().toZoneId());
	}

	/**
	 * @param time
	 * @param formatter
	 * @return
	 */
	public static String toString(LocalDateTime time, String formatter) {
		return Optional.ofNullable(time).map(e -> e.format(DateTimeFormatter.ofPattern(formatter))).orElse("");
	}
}
