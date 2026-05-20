package com.fsap.monitor.sftp.vo;

import java.util.List;
import java.util.Optional;
import java.util.Vector;

import com.fsap.monitor.sftp.transfer.FileEntry;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.ChannelSftp.LsEntrySelector;
import com.jcraft.jsch.SftpATTRS;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2022 All Rights Reserved. <br>
 * 日期：2022/09/21<br>
 * 作者：KaRno<br>
 * 程式代號: SftpFileNameSelector.java<br>
 * 程式說明: SFTP FileName Filter<br>
 * ======================================================================
 */
public class SftpFileNameSelector implements SftpEntrySelector {

	private final Vector<FileEntry> vector = new Vector<FileEntry>();
	private final String fileName;

	public SftpFileNameSelector(String fileName) {
		this.fileName = fileName;
	}

	@Override
	public int select(LsEntry entry) {
		final String entryFileName = entry.getFilename();
		boolean isFile = Optional.ofNullable(entry).map(LsEntry::getAttrs).map(SftpATTRS::isReg).orElse(false);

		if (isFile && entryFileName.equals(fileName)) {
			vector.addElement(new SftpFileEntiry(entry));
		}
		return LsEntrySelector.CONTINUE;
	}

	@Override
	public List<FileEntry> toFileEntry() {
		return vector;
	}

}
