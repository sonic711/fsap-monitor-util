package com.fsap.monitor.sftp.vo;

import java.time.LocalDateTime;

import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.utils.DateUtils;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.SftpATTRS;

public class SftpFileEntiry implements FileEntry {
	private final LsEntry lsEntry;
	private final Long size;
	private final String fileName;
	private final Integer mtime;
	private final boolean isFile;

	public SftpFileEntiry(LsEntry lsEntry) {
		this.lsEntry = lsEntry;
		SftpATTRS attrs = lsEntry.getAttrs();
		this.size = attrs.getSize();
		this.fileName = lsEntry.getFilename();
		this.isFile = attrs.isReg();
		this.mtime = attrs.getMTime();
	}

	@Override
	public Object original() {
		return lsEntry;
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
		return DateUtils.toLocalDateTime(mtime.longValue());
	}

	@Override
	public boolean isFile() {
		return isFile;
	}

}
