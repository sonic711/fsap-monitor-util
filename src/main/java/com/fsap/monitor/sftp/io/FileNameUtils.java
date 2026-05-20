package com.fsap.monitor.sftp.io;

import java.net.URLEncoder;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fsap.monitor.sftp.transfer.exception.VerificationException;
import com.fsap.monitor.sftp.utils.DateUtils;
import com.fsap.monitor.sftp.utils.Utils;

public class FileNameUtils extends org.apache.commons.io.FilenameUtils {

	private static Logger LOG = LoggerFactory.getLogger(FileNameUtils.class);

	private final static String DEFAULT_ENCODING = "UTF-8";

	private final static String REPLACE_SEPARATOR = "[/]+|[\\\\]+";
	private final static String SPLIT_SEPARATOR = "[/|\\\\]";

	/**
	 * 驗證副檔名
	 *
	 * @param fileName
	 * @param extension
	 * @return
	 */
	public final static boolean verify(String fileName, CharSequence... extension) {
		final String fileExtension = getExtension(fileName);
		return StringUtils.equalsAnyIgnoreCase(fileExtension, extension);
	}

	/**
	 * 驗證副檔名
	 *
	 * @param <X>
	 * @param exceptionSupplier
	 * @param fileName
	 * @param extension
	 * @throws X
	 */
	public final static void verifyOrThrow(final String errorMessage, final String fileName, final CharSequence... extension) throws VerificationException {
		if (!verify(fileName, extension)) {
			throw new VerificationException(errorMessage);
		}
	}

	/**
	 * 檔名輸出Encode
	 *
	 * @param fileName
	 * @return
	 */
	public final static String fileNameEncodeURL(String fileName) {
		try {
			if (StringUtils.isNotBlank(fileName)) {
				return "attachment; filename=\"" + URLEncoder.encode(fileName, DEFAULT_ENCODING).replace("+", "%20") + "\"";
			} else {
				LOG.warn("FileNameEncodeURL Is Blank");
			}
		} catch (Exception e) {
			LOG.warn("Is Not Supported : {}", DEFAULT_ENCODING);
		}
		return "attachment; filename=\"" + Utils.UUID() + "\"";
	}

	/**
	 * 統一資料夾分隔符號
	 *
	 * @param path
	 * @return
	 */
	public final static String replaceSeparator(String path) {
		return path.replaceAll(REPLACE_SEPARATOR, "/");
	}

	/**
	 * 統一資料夾分隔符號，並移除後分隔符號
	 *
	 * @param path
	 * @return
	 */
	public final static String replaceSeparatorNoEnd(String path) {
		String temp = path.replaceAll(REPLACE_SEPARATOR, "/");
		if (temp.length() > 2) {
			temp = StringUtils.removeEnd(temp, "/");
		}
		return temp;
	}

	/**
	 * 統一資料夾分隔符號，並移除前後分隔符號
	 *
	 * @param path
	 * @return
	 */
	public final static String replaceSeparatorNoStartAndEnd(String path) {
		String temp = path.replaceAll(REPLACE_SEPARATOR, "/");
		temp = StringUtils.removeStart(temp, "/");
		temp = StringUtils.removeEnd(temp, "/");
		return temp;
	}

	/**
	 * 以資料夾分隔符號分離資料夾名稱
	 *
	 * @param path
	 * @return
	 */
	public final static List<String> splitSeparator(String path) {
		return Arrays.asList(path.split(SPLIT_SEPARATOR));
	}

	/**
	 * 路徑時間參數轉換
	 *
	 * @param filePath
	 * @return
	 */
	public final static String matchPath(String filePath) {
		if (filePath.matches(".*(?i)\\$\\{SYSDATE\\}.*")) {
			String dateStr = DateUtils.now(DateUtils.DATE_NOTHING);
			filePath = filePath.replaceAll("(?i)\\$\\{SYSDATE\\}", dateStr);
		}
		return filePath;
	}

}
