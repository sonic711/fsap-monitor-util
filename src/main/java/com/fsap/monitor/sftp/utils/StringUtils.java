package com.fsap.monitor.sftp.utils;

import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2022 All Rights Reserved. <br>
 * 日期：2022/05/30<br>
 * 作者：KaRno<br>
 * 程式代號: StringUtils.java<br>
 * 程式說明: 字串處理工具, org.apache.commons.lang3.StringUtils 擴充<br>
 * ======================================================================
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

	private final static Pattern LINE_SEPARATOR_PATTERN = Pattern.compile("\\R");

	/**
	 * 物件 TO String , 物件為空值時回傳空字串
	 *
	 * @param obj
	 * @return
	 */
	public static String toString(Object obj) {
		return Objects.isNull(obj) ? EMPTY : obj.toString();
	}

	public static String toUpperCase(Object obj) {
		return toString(obj).toUpperCase(Locale.US);
	}

	/**
	 * 物件 TO String , 物件為空值時回傳指定字串
	 *
	 * @param obj
	 * @param defStr
	 * @return
	 */
	public static String toString(Object obj, String defStr) {
		return Objects.isNull(obj) ? defStr : obj.toString();
	}

	/**
	 * 字串拼接
	 *
	 * @param elements
	 *            字串集
	 * @return
	 */
	public static String join(CharSequence... elements) {
		StringBuffer sb = new StringBuffer();
		for (CharSequence cs : elements) {
			sb.append(cs);
		}
		return sb.toString();
	}

	/**
	 * 字串拼接<BR>
	 * 字串為空值、空字串、空白，自動忽略<BR>
	 *
	 * @param delimiter
	 *            分隔符號
	 * @param elements
	 *            字串集
	 * @return
	 */
	public static String joinIfNotBlank(CharSequence delimiter, String... elements) {
		return joinIfNotBlank(delimiter, Arrays.asList(elements));
	}

	/**
	 * 字串拼接<BR>
	 * 字串為空值、空字串、空白，自動忽略<BR>
	 *
	 * @param delimiter
	 * @param elements
	 * @return
	 */
	public static String joinIfNotBlank(CharSequence delimiter, List<String> elements) {
		if (Objects.isNull(elements)) {
			return EMPTY;
		}
		StringJoiner joiner = new StringJoiner(delimiter);
		for (CharSequence cs : elements) {
			if (isNotBlank(cs)) {
				joiner.add(cs);
			}
		}
		return joiner.toString();
	}

	/**
	 *
	 * @param prefix
	 *            前綴
	 * @param suffix
	 *            後綴
	 * @param content
	 *            本文
	 * @return
	 */
	public static String fix(String prefix, String suffix, String content) {
		String temp = toString(content);
		if (StringUtils.isBlank(temp)) {
			return EMPTY;
		}
		return join(prefix, temp, suffix);
	}

	/**
	 * 半形轉全形
	 *
	 * @param input
	 *            String.
	 * @return 全形字元串.
	 */
	public static String toSBC(String input) {

		char c[] = input.toCharArray();
		for (int i = 0; i < c.length; i++) {
			if (c[i] == ' ') {
				c[i] = '\u3000';
			} else if (c[i] < '\177') {
				c[i] = (char) (c[i] + 65248);
			}
		}
		return new String(c);
	}

	/**
	 *
	 * @param obj
	 * @return
	 */
	public static Optional<String> notBlank(String obj) {
		return Optional.ofNullable(obj).filter(StringUtils::isNotBlank);
	}

	/**
	 * 字串以換行符號切割<BR>
	 *
	 * @param source
	 *            來源字串
	 * @return
	 */
	public static List<String> splitLineSeparator(String value) {
		if (isBlank(value)) {
			return new ArrayList<>(0);
		}
		return Utils.toList(value.split("\\R"));
	}

	/**
	 * 是否有換行符號
	 *
	 * @param value
	 *            來源字串
	 * @return
	 */
	public static boolean hasLineSeparator(String value) {
		return LINE_SEPARATOR_PATTERN.matcher(value).find();
	}

	/**
	 * 字串截斷,用於寫入DB欄位大小限制<BR>
	 *
	 * @param str
	 *            原始字串
	 * @param maxLength
	 *            DB欄位最大長度
	 */
	public final static String truncate(String str, int maxLength) {
		return truncate(str, maxLength, "");
	}

	/**
	 * 字串截斷,用於寫入DB欄位大小限制<BR>
	 * BASIC_LATIN 1 : Other 3<BR>
	 *
	 * @param str
	 *            原始字串
	 * @param maxLength
	 *            DB欄位最大長度
	 * @param suffix
	 *            超出最大長度，後綴說明
	 */
	public final static String truncate(String str, int maxLength, String suffix) {
		if (Objects.isNull(str)) {
			return str;
		}

		if (maxLength < 0) {
			throw new IllegalArgumentException("Illegal Capacity: " + maxLength);
		}

		String tempSuffix = StringUtils.defaultString(suffix);
		int baseLength = length(StringUtils.defaultIfEmpty(tempSuffix, "").toCharArray());
		if (baseLength > maxLength) {
			return truncate(tempSuffix, maxLength);
		}
		StringBuffer sb = new StringBuffer();
		int i = 0;
		char[] charArray = str.toCharArray();
		for (char c : charArray) {
			int tempLength = length(c);
			if ((i + tempLength + baseLength) <= maxLength) {
				i += length(c);
				sb.append(c);
			} else {
				sb.append(tempSuffix);
				break;
			}
		}
		return sb.toString();
	}

	/**
	 * BASIC_LATIN 1 : Other 3
	 *
	 * @param c
	 * @return
	 */
	private final static int length(char c) {
		return UnicodeBlock.BASIC_LATIN.equals(UnicodeBlock.of(c)) ? 1 : 3;
	}

	/**
	 * BASIC_LATIN 1 : Other 3
	 *
	 * @param cs
	 * @return
	 */
	private final static int length(char[] cs) {
		int i = 0;
		for (char c : cs) {
			i += length(c);
		}
		return i;
	}

	/**
	 * BASIC_LATIN 1 : Other 2
	 *
	 * @param str
	 * @return
	 */
	public final static Integer length2(String str) {
		if (Objects.isNull(str)) {
			return 0;
		}
		int i = 0;
		char[] charArray = str.toCharArray();
		for (char c : charArray) {
			UnicodeBlock u = UnicodeBlock.of(c);
			boolean isBasicLatin = UnicodeBlock.BASIC_LATIN.equals(u);
			i += isBasicLatin ? 1 : 2;
		}
		return i;
	}

	/**
	 * 截取指定字串前<BR>
	 * 比對無效時回傳空字串<BR>
	 * 與原生substringBefore不同<BR>
	 * 差異請查看 org.apache.commons.lang3.StringUtils#substringBefore(String, String)
	 *
	 * @see org.apache.commons.lang3.StringUtils#substringBefore(String, String)
	 *
	 *
	 * @param str
	 * @param separator
	 * @return
	 */
	public static String substrBefore(final String str, final String separator) {
		if (isBlank(str) || isBlank(separator)) {
			return EMPTY;
		}
		final int pos = str.indexOf(separator);
		if (pos == INDEX_NOT_FOUND) {
			return EMPTY;
		}
		return str.substring(0, pos);
	}

	/**
	 * 截取指定字串後<BR>
	 * 比對無效時回傳空字串<BR>
	 * 與原生substringBefore不同<BR>
	 * 差異請查看 org.apache.commons.lang3.StringUtils#substringAfter(String, String)
	 *
	 * @see org.apache.commons.lang3.StringUtils#substringAfter(String, String)
	 *
	 *
	 * @param str
	 * @param separator
	 * @return
	 */
	public static String substrAfter(final String str, final String separator) {
		if (isBlank(str) || isBlank(separator)) {
			return EMPTY;
		}
		final int pos = str.indexOf(separator);
		if (pos == INDEX_NOT_FOUND) {
			return EMPTY;
		}
		return str.substring(pos + separator.length());
	}

	/**
	 * 基本拉丁字母 (Unicode區段)<BR>
	 * null視為 false
	 *
	 * @param str
	 * @return
	 */
	public static boolean onlyBasicLatin(final String str) {
		if (Objects.isNull(str)) {
			return false;
		}
		for (char c : str.toCharArray()) {
			if (!UnicodeBlock.BASIC_LATIN.equals(UnicodeBlock.of(c))) {
				return false;
			}
		}
		return true;
	}


	/**
	 * 左方裁剪
	 *
	 * @param value
	 *            字串
	 * @param maxLength
	 *            最大長度
	 * @return
	 */
	public static String leftCutout(String value, int maxLength) {
		if (value.length() > maxLength) {
			int startPoint = value.length() - maxLength;
			return value.substring(startPoint, value.length());
		}
		return value;
	}

	/**
	 * 右方裁剪
	 *
	 * @param value
	 *            字串
	 * @param maxLength
	 *            最大長度
	 * @return
	 */
	public static String rightCutout(String value, int maxLength) {
		if (value.length() > maxLength) {
			return value.substring(0, maxLength);
		}
		return value;
	}

}
