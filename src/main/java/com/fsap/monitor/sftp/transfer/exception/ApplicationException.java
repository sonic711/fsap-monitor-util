package com.fsap.monitor.sftp.transfer.exception;

import java.util.List;


public interface ApplicationException {
	/**
	 * 錯誤代碼
	 *
	 * @return
	 */
	MessageCode<?> getCode();

	/**
	 * 補充說明資訊
	 *
	 * @return
	 */
	List<String> getSupplemental();
}
