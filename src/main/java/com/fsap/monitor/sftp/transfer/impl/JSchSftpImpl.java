package com.fsap.monitor.sftp.transfer.impl;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Vector;
import java.util.stream.Collectors;

import org.apache.velocity.shaded.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fsap.monitor.sftp.io.FileNameUtils;
import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.transfer.FileTransfer;
import com.fsap.monitor.sftp.transfer.exception.MessageCode;
import com.fsap.monitor.sftp.transfer.exception.TransferCodeEnum;
import com.fsap.monitor.sftp.transfer.exception.TransferException;
import com.fsap.monitor.sftp.vo.SftpFileEntiry;
import com.fsap.monitor.sftp.vo.SftpFileNameSelector;
import com.fsap.monitor.sftp.vo.SftpIgnoreFileExtensionSelector;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

public class JSchSftpImpl implements FileTransfer {

	private Logger log = LoggerFactory.getLogger(getClass());

	private final JSch jsch = new JSch();
	private Session session;
	private ChannelSftp channel;
	private final String ip;
	private final Integer port;
	private final String username;
	private final String code;

	public JSchSftpImpl(String ip, Integer port, String username, String code) {
		this.ip = ip;
		this.port = port;
		this.username = username;
		this.code = code;
	}

	@Override
	public void connection() throws TransferException {
		if (!sessionIsConnected()) {
			createSession();
		}
		if (!channelIsConnected()) {
			createChannel();
		}
	}

	@Override
	public void disconnect() {
		try {
			if (channelIsConnected()) {
				channel.quit();
				channel.disconnect();
			}
			if (sessionIsConnected()) {
				session.disconnect();
			}
		} catch (Exception e) {
			log.warn("Try Disconnect Failed");
		}
		log.info("Disconnect Sftp");
	}

	@Override
	public String pwd() throws TransferException {
		try {
			return getChannel().pwd();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00006, e);
		}
		return null;
	}

	@Override
	public void cd(String path) throws TransferException {
		try {
			getChannel().cd(path);
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00008, e);
		}
	}

	@Override
	public void cdWithMkdir(String path) throws TransferException {
		try {
			getChannel().cd(path); // 嘗試進入目標目錄
		} catch (SftpException e) {
			if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
				try {
					String[] folders = path.split("/");
					String currentPath = "";

					for (String folder : folders) {
						if (folder.isEmpty())
							continue; // 避免處理開頭的空字串
						currentPath += "/" + folder;
						try {
							getChannel().cd(currentPath); // 嘗試進入當前目錄
						} catch (SftpException ex) {
							getChannel().mkdir(currentPath); // 若目錄不存在，則建立
							log.debug("Create Directory: {}", currentPath);
							getChannel().cd(currentPath);   // 進入剛建立的目錄
						}
					}
				} catch (SftpException ex) {
					errorHandler(TransferCodeEnum.E120_00014, ex);
				}
			} else {
				errorHandler(TransferCodeEnum.E120_00008, e);
			}
		}
	}

	@Override
	public void mkdir(String path) throws TransferException {
		try {
			String tempPath = FileNameUtils.replaceSeparatorNoEnd(path);
			getChannel().mkdir(tempPath);
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00008, e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<FileEntry> ls() throws TransferException {
		try {
			Vector<LsEntry> list = channel.ls(channel.pwd());
			return list.stream().map(SftpFileEntiry::new).collect(Collectors.toList());
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00007, e);
		}
		return Collections.emptyList();
	}

	@Override
	public List<FileEntry> ls(String filePath) throws TransferException {
		try {
			String fileName = FilenameUtils.getName(filePath);
			SftpFileNameSelector selector = new SftpFileNameSelector(fileName);
			getChannel().ls(pwd(), selector);
			return selector.toFileEntry();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00007, e);
		}
		return Collections.emptyList();
	}

	@Override
	public List<FileEntry> lsIgnoreExtension(String filePath) throws TransferException {
		try {
			String fileName = FilenameUtils.getName(filePath);
			SftpIgnoreFileExtensionSelector selector = new SftpIgnoreFileExtensionSelector(fileName);
			getChannel().ls(pwd(), selector);
			return selector.toFileEntry();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00007, e);
		}
		return Collections.emptyList();
	}

	@Override
	public Optional<FileEntry> find(String filePath) throws TransferException {
		return ls(filePath).stream().findFirst();
	}

	@Override
	public void get(String path, OutputStream outputStream) throws TransferException {
		try {
			getChannel().get(path, outputStream);
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00009, e);
		}
	}

	@Override
	public InputStream get(String path) throws TransferException {
		try {
			return getChannel().get(path);
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00009, e);
		}
		return null;
	}

	@Override
	public void put(String path, InputStream fileInputStream) throws TransferException {
		try {
			getChannel().put(fileInputStream, path);
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00010, e);
		}
	}

	@Override
	public void delete(String path) throws TransferException {
		try {
			getChannel().rm(path);
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00011, e);
		}
	}

	private boolean sessionIsConnected() {
		return Objects.nonNull(session) && session.isConnected();
	}

	private boolean channelIsConnected() {
		return Objects.nonNull(channel) && channel.isConnected();
	}

	private void createSession() throws TransferException {
		try {
			session = jsch.getSession(username, ip, port);
			if (!sessionIsConnected()) {
				//log.info("Connect Sftp Create Session To {} ", EscapeUtils.escape(ip));
				Properties sshConfig = new Properties();
				sshConfig.put("StrictHostKeyChecking", "no");
				session.setConfig(sshConfig);
				session.setPassword(code);
				session.setTimeout(2 * 6000);
				session.connect();
			}
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00012, e);
		}
	}

	private void createChannel() throws TransferException {
		try {
			if (!channelIsConnected()) {
				//log.info("Connect Sftp Open Channel To {} ", EscapeUtils.escape(ip));
				channel = (ChannelSftp) session.openChannel("sftp");
				channel.connect();
			}
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00012, e);
		}
	}

	private ChannelSftp getChannel() throws TransferException {
		return this.channel;
	}

	private void errorHandler(MessageCode<?> code, Throwable t) throws TransferException {
		if (t instanceof TransferException) {
			throw (TransferException) t;
		}
		throw new TransferException(code, t);
	}

}
