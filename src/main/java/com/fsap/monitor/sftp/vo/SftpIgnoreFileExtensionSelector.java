package com.fsap.monitor.sftp.vo;

import java.util.List;
import java.util.Optional;
import java.util.Vector;

import org.apache.commons.compress.utils.FileNameUtils;

import com.fsap.monitor.sftp.transfer.FileEntry;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.ChannelSftp.LsEntrySelector;
import com.jcraft.jsch.SftpATTRS;

public class SftpIgnoreFileExtensionSelector implements SftpEntrySelector {

	private final Vector<FileEntry> vector = new Vector<FileEntry>();
	private final String fileName;
	private final String fileBaseName;

	public SftpIgnoreFileExtensionSelector(String fileName) {
		this.fileName = fileName;
		this.fileBaseName = FileNameUtils.getBaseName(fileName);
	}

	@Override
	public int select(LsEntry entry) {
		boolean isFile = Optional.ofNullable(entry).map(LsEntry::getAttrs).map(SftpATTRS::isReg).orElse(false);
		if (isFile) {
			final String entryFileName = entry.getFilename();
			final String entryFileBaseName = FileNameUtils.getBaseName(entryFileName);
			if (fileName.equalsIgnoreCase(entryFileName) || fileBaseName.equalsIgnoreCase(entryFileBaseName)) {
				vector.addElement(new SftpFileEntiry(entry));
			}
		}
		return LsEntrySelector.CONTINUE;
	}

	@Override
	public List<FileEntry> toFileEntry() {
		return vector;
	}
}
