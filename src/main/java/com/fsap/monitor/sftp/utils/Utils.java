package com.fsap.monitor.sftp.utils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

public class Utils {

	/**
	 * UUID(36碼)
	 *
	 * @return
	 */
	public final static String UUID() {
		return UUID.randomUUID().toString();
	}


	/**
	 * 遮罩全部
	 *
	 * @param input
	 * @return
	 */
	public final static String maskAll(String input) {
		if (StringUtils.isBlank(input)) {
			return StringUtils.EMPTY;
		}
		return input.replaceAll("[^ ]", "*");
	}

	/**
	 * 只顯示前三碼，其餘遮罩
	 *
	 * @param input
	 * @return
	 */
	public final static String mask(String input) {
		if (StringUtils.isBlank(input)) {
			return StringUtils.EMPTY;
		}
		return input.replaceAll("(?<=.{3}).", "*");
	}

	/**
	 * 交易/程式執行 批號(32碼) <BR>
	 * 未定義ID時為七碼0<BR>
	 *
	 * @param id
	 *            (功能 或 JOB ID)7碼
	 * @return
	 */
	public final static String processId(String id) {
		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern(DateUtils.TIMESTAMP_NOTHING));
		String uuid = UUID.randomUUID().toString().replaceAll("-", "");
		StringBuilder sb = new StringBuilder();
		sb.append(StringUtils.defaultString(id, "0000000")).append("_").append(time).append("_").append(StringUtils.substring(uuid, 23));
		return sb.toString().toUpperCase(Locale.US);
	}

	/**
	 * @param messagePattern
	 * @param argArray
	 * @return
	 */
	public final static String format(String messagePattern, Object... argArray) {
		if (Objects.isNull(argArray) || argArray.length < 1) {
			return messagePattern;
		}
		FormattingTuple t = MessageFormatter.arrayFormat(messagePattern, argArray);
		return t.getMessage();
	}

	@SafeVarargs
	public final static <T> List<T> toList(T... obj) {
		return toStream(obj).collect(Collectors.toList());
	}

	@SafeVarargs
	public final static <T> Stream<T> toStream(T... obj) {
		return Optional.ofNullable(obj).map(Arrays::stream).orElseGet(Stream::empty)//
				.filter(Objects::nonNull);
	}

	public final static LinkedList<String> toLinkedList(String... obj) {
		return Optional.ofNullable(obj).map(Arrays::stream).orElseGet(Stream::empty)//
				.filter(StringUtils::isNotBlank).collect(Collectors.toCollection(LinkedList::new));
	}

	public final static <T> String printStack(Throwable throwable, int limit) {
		return toStream(throwable.getStackTrace()).limit(limit).filter(Objects::nonNull).map(Objects::toString).collect(Collectors.joining(System.lineSeparator()));
	}

	public final static String printStack(Throwable throwable) {
		return ExceptionUtils.getStackTrace(throwable);
	}

	public final static Integer makerError() {
		return 1 / 0;
	}
}
