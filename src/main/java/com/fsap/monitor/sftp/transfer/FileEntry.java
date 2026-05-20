package com.fsap.monitor.sftp.transfer;

import java.time.LocalDateTime;

public interface FileEntry {
	/**
	 * 取得原生物件
	 *
	 * @return
	 */
	Object original();

	/**
	 * 取得檔案大小
	 *
	 * @return
	 */
	Long getSize();

	/**
	 * 取得檔案名稱
	 *
	 * @return
	 */
	String getFileName();

	/**
	 * 最後異動時間
	 *
	 * @return
	 */
	LocalDateTime getLastModifyTime();

	/**
	 * 是否為檔案
	 *
	 * @return
	 */
	boolean isFile();
}
