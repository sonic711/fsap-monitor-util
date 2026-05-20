package com.fsap.monitor.sftp.transfer;

import java.util.stream.Stream;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2022 All Rights Reserved. <br>
 * 日期：2023/09/01<br>
 * 作者：<br>
 * 程式代號: TransferType.java<br>
 * 程式說明: 檔案傳輸協定<br>
 * ======================================================================
 */
public enum TransferType {
	FTP(21),
	SFTP(22),
	FILE(-1);

	private final Integer defaultPort;

	TransferType(Integer defaultPort) {
		this.defaultPort = defaultPort;
	}

	public static TransferType getEnum(String val) {
		return Stream.of(values()).filter(e -> {
			int len = e.name().length();
			return e.name().regionMatches(true, 0, val, 0, len);
		}).findAny().orElseThrow(() -> new IllegalArgumentException("Invalid TransferType"));
	}

	public Integer getDefaultPort() {
		return defaultPort;
	}

}
