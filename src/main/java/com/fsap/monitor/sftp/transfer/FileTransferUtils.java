package com.fsap.monitor.sftp.transfer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fsap.monitor.sftp.transfer.impl.ApacheFtpImpl;
import com.fsap.monitor.sftp.transfer.impl.JSchSftpImpl;
import com.fsap.monitor.sftp.utils.StringUtils;

public class FileTransferUtils {
	/** IP格式 過濾 */
	private final static Pattern IPADDRESS_PATTERN = Pattern.compile("(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");

	public static FileTransfer newInstance(ConnectInfo connectInfo) {
		return newInstance(connectInfo.getUrl(), connectInfo.getUsername(), connectInfo.getCode());
	};

	private static FileTransfer newInstance(String hostUrl, String username, String code) {
		Matcher ipMatcher = IPADDRESS_PATTERN.matcher(hostUrl);
		if (ipMatcher.find()) {
			String ip = ipMatcher.group();
			TransferType type = TransferType.getEnum(hostUrl);
			Integer port = getPortFromHostUrl(type, hostUrl, ip);
			return newInstance(type, hostUrl, ip, port, username, code);
		}
		return newInstance(TransferType.FILE, hostUrl, "", null, username, code);
	}

	private static FileTransfer newInstance(TransferType type, String hostUrl, String ip, Integer port, String username, String code) {
		switch (type) {
		case FTP:
			return new ApacheFtpImpl(ip, port, username, code);
		case SFTP:
			return new JSchSftpImpl(ip, port, username, code);
		case FILE:
			throw new IllegalArgumentException("File handling not implemented");
		}
		throw new IllegalArgumentException("Invalid TransferType");
	}

	private static Integer getPortFromHostUrl(TransferType type, String hostUrl, String ip) {
		int point = hostUrl.replaceAll(".+://" + StringUtils.toString(ip), "").lastIndexOf(":");
		if (point >= 0) {
			return verifyPort(hostUrl.substring(hostUrl.lastIndexOf(":") + 1, hostUrl.length()));
		}
		return type.getDefaultPort();
	}

	private static Integer verifyPort(String port) {
		Integer tempPort = Integer.valueOf(port);
		if (1 <= tempPort && 65535 >= tempPort) {
			return tempPort;
		}
		throw new IllegalArgumentException("Invalid PORT Number");
	}
}
