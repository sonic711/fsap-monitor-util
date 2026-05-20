package com.fsap.monitor.sftp.transfer;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

import com.fsap.monitor.sftp.transfer.exception.TransferException;

public interface FileTransfer extends AutoCloseable {

	/**
	 * 連線
	 *
	 * @throws TransferException
	 */
	void connection() throws TransferException;

	/**
	 * 離線
	 */
	void disconnect();

	/**
	 * try catch AutoCloseable自動執行離線<BR>
	 */
	@Override
	default void close() {
		disconnect();
	}

	/**
	 * 目前資料夾
	 *
	 * @return
	 * @throws TransferException
	 */
	String pwd() throws TransferException;

	/**
	 * 切換目錄<BR>
	 *
	 * @param path
	 * @throws TransferException
	 */
	void cd(String path) throws TransferException;

	/**
	 * 切換目錄，若不存在則創建目錄後在切換
	 *
	 * @param path
	 * @throws TransferException
	 */
	void cdWithMkdir(String path) throws TransferException;

	/**
	 * 建立目錄
	 *
	 * @param path
	 * @throws TransferException
	 */
	void mkdir(String path) throws TransferException;

	/**
	 * 回傳目前目錄內容資訊
	 *
	 * @return
	 * @throws TransferException
	 */
	List<FileEntry> ls() throws TransferException;

	/**
	 * 回傳指定檔案內容資訊<BR>
	 *
	 * @param filePath
	 * @return
	 * @throws TransferException
	 */
	List<FileEntry> ls(String filePath) throws TransferException;

	/**
	 * 回傳指定檔案內容資訊(忽略副檔名及檔名大小寫)<BR>
	 *
	 * @param filePath
	 * @return
	 * @throws TransferException
	 */
	List<FileEntry> lsIgnoreExtension(String filePath) throws TransferException;

	/**
	 * 下載檔案
	 *
	 * @param path
	 * @param outputStream
	 * @throws TransferException
	 */
	void get(String path, OutputStream outputStream) throws TransferException;

	/**
	 * 查詢單一檔案內容資訊
	 *
	 * @param filePath
	 * @return
	 * @throws TransferException
	 */
	Optional<FileEntry> find(String filePath) throws TransferException;

	/**
	 * 下載檔案
	 *
	 * @param path
	 * @return
	 * @throws TransferException
	 */
	InputStream get(String path) throws TransferException;

	/**
	 * 上傳
	 *
	 * @param path
	 * @param fileInputStream
	 * @throws TransferException
	 */
	void put(String path, InputStream fileInputStream) throws TransferException;

	/**
	 * 刪除
	 *
	 * @param path
	 * @throws TransferException
	 */
	void delete(String path) throws TransferException;
}
