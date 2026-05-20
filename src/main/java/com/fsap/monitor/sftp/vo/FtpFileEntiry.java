package com.fsap.monitor.sftp.vo;

import java.time.LocalDateTime;
import java.util.Calendar;

import org.apache.commons.net.ftp.FTPFile;

import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.utils.DateUtils;

public class FtpFileEntiry implements FileEntry {
	private final FTPFile ftpFile;
	private final Long size;
	private final String fileName;
	private final Calendar lastModifyTime;
	private final boolean isFile;

	public FtpFileEntiry(FTPFile ftpFile) {
		this.ftpFile = ftpFile;
		this.fileName = ftpFile.getName();
		this.size = ftpFile.getSize();
		this.isFile = ftpFile.isFile();
		this.lastModifyTime = ftpFile.getTimestamp();
	}

	@Override
	public Object original() {
		return ftpFile;
	}

	@Override
	public Long getSize() {
		return size;
	}

	@Override
	public String getFileName() {
		return fileName;
	}

	@Override
	public LocalDateTime getLastModifyTime() {
		return DateUtils.toLocalDateTime(this.lastModifyTime);
	}

	@Override
	public boolean isFile() {
		return isFile;
	}

}
