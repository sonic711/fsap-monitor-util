package com.fsap.monitor.sftp.transfer.exception;

public interface MessageCode<T extends Enum<T>> {

	/**
	 * 代碼列舉
	 *
	 * @return
	 */
	T getEnum();

	/**
	 * 代碼值
	 *
	 * @return
	 */
	String getCode();

	/**
	 * 代碼分類
	 *
	 * @return
	 */
	String getType();

	/**
	 * 代碼訊息
	 *
	 * @return
	 */
	String getMessage();

}
