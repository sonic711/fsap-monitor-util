package com.fsap.monitor.sftp.transfer;

public interface ConnectInfo {

	/**
	 * 主機URL
	 *
	 * @return
	 */
	String getUrl();

	/**
	 * 用戶名稱
	 *
	 * @return
	 */
	String getUsername();

	/**
	 * 用戶驗證碼
	 *
	 * @return
	 */
	String getCode();
}
