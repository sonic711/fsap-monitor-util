package com.fsap.monitor.sftp.vo;

import java.util.List;

import com.fsap.monitor.sftp.transfer.FileEntry;
import com.jcraft.jsch.ChannelSftp.LsEntrySelector;



/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2022 All Rights Reserved. <br>
 * 日期：2022/09/21<br>
 * 作者：KaRno<br>
 * 程式代號: EntrySelector.java<br>
 * 程式說明: Sftp Filter<br>
 * ======================================================================
 */
public interface SftpEntrySelector extends LsEntrySelector {

	List<FileEntry> toFileEntry();

}
