package com.fsap.monitor.sftp.transfer.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.net.MalformedServerReplyException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPConnectionClosedException;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fsap.monitor.sftp.io.FileNameUtils;
import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.transfer.FileTransfer;
import com.fsap.monitor.sftp.transfer.exception.FtpCodeEnum;
import com.fsap.monitor.sftp.transfer.exception.MessageCode;
import com.fsap.monitor.sftp.transfer.exception.TransferCodeEnum;
import com.fsap.monitor.sftp.transfer.exception.TransferException;
import com.fsap.monitor.sftp.vo.FtpFileEntiry;

public class ApacheFtpImpl implements FileTransfer {

	private Logger log = LoggerFactory.getLogger(getClass());

	private final FTPClient ftpClient = new FTPClient();
	private final String ip;
	private final Integer port;
	private final String username;
	private final String code;
	private String connectionType;
	private String transferType;

	public ApacheFtpImpl(String ip, Integer port, String username, String code) {
		this.ip = ip;
		this.port = port;
		this.username = username;
		this.code = code;
	}

	@Override
	public void connection() throws TransferException {
		try {
			FTPClientConfig config = new FTPClientConfig();
			config.setServerTimeZoneId("UTC");
			ftpClient.configure(config);
			ftpClient.setConnectTimeout(5000);
			ftpClient.setAutodetectUTF8(true);
			ftpClient.setBufferSize(1024);
			doConnect();
			doLogin();
			setConnectMode();
			setTransferMode();

		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00001, e);
		}
	}

	private void doConnect() throws TransferException, SocketException, IOException {
		log.info("Ftp Connect {}:{}", ip, port);
		ftpClient.connect(ip, port);
		resolve();
	}

	private Boolean doLogin() throws IOException, TransferException {
		Boolean isLogin = ftpClient.login(username, code);
		resolve();
		return isLogin;
	}

	@Override
	public void disconnect() {
		doLogout();
		doDisconnect();
		log.info("Ftp Disconnect");
	}

	@Override
	public String pwd() throws TransferException {
		try {
			String pwd = ftpClient.printWorkingDirectory();
			resolve();
			return pwd;
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00006, e);
		}
		return null;
	}

	@Override
	public void cd(String path) throws TransferException {
		try {
			String tempPath = FileNameUtils.replaceSeparatorNoEnd(path);
			ftpClient.changeWorkingDirectory(tempPath);
			resolve();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00008, e);
		}
	}

	@Override
	public void cdWithMkdir(String path) throws TransferException {
		try {
			String tempPath = FileNameUtils.replaceSeparatorNoEnd(path);
			ftpClient.changeWorkingDirectory(tempPath);
			resolve();
		} catch (TransferException e) {
			// 檢查是否為目錄不存在的錯誤，逐層建立目錄
			try {
				String currentPath = "";
				for (String folder : splitPath(path)) {
					if (folder.isEmpty()) {
						continue;
					}
					// 正確構建路徑，避免雙斜線
					if (currentPath.isEmpty() || currentPath.endsWith("/")) {
						currentPath = currentPath + folder;
					} else {
						currentPath = currentPath + "/" + folder;
					}
					try {
						String tempCurrentPath = FileNameUtils.replaceSeparatorNoEnd(currentPath);
						ftpClient.changeWorkingDirectory(tempCurrentPath);
						resolve();
					} catch (TransferException ex) {
						// 目錄不存在，建立目錄
						ftpClient.makeDirectory(currentPath);
						resolve();
						log.debug("Create Directory: {}", currentPath);

						// 進入剛建立的目錄
						String tempCurrentPath = FileNameUtils.replaceSeparatorNoEnd(currentPath);
						ftpClient.changeWorkingDirectory(tempCurrentPath);
						resolve();
					}
				}
			} catch (Exception ex) {
				errorHandler(TransferCodeEnum.E120_00014, ex);
			}
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00008, e);
		}
	}

	/**
	 * FTP協議不能直接創建多層級資料夾<BR>
	 */
	@Override
	public void mkdir(String path) throws TransferException {
		try {
			for (String dir : splitPath(path)) {
				ftpClient.makeDirectory(dir);
			}
			resolve();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00014, e);
		}
	}

	@Override
	public List<FileEntry> ls() throws TransferException {
		try {
			List<FileEntry> list = Stream.of(ftpClient.listFiles()).map(FtpFileEntiry::new).collect(Collectors.toList());
			resolve();
			return list;
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00007, e);
		}
		return Collections.emptyList();
	}

	@Override
	public List<FileEntry> ls(String filePath) throws TransferException {
		try {

			List<FileEntry> list = Stream.of(ftpClient.listFiles(filePath)).map(FtpFileEntiry::new).collect(Collectors.toList());
			resolve();
			return list;
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00007, e);
		}
		return Collections.emptyList();
	}

	@Override
	public Optional<FileEntry> find(String filePath) throws TransferException {
		return ls(filePath).stream().filter(FileEntry::isFile).findFirst();
	}

	@Override
	public List<FileEntry> lsIgnoreExtension(String filePath) throws TransferException {
		try {
			String fileName = FileNameUtils.getName(filePath);
			List<FileEntry> list = Stream.of(ftpClient.listFiles(ftpClient.printWorkingDirectory(), filterIgnoreExtension(fileName)))//
					.map(FtpFileEntiry::new).collect(Collectors.toList());
			resolve();
			return list;
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00007, e);
		}
		return Collections.emptyList();
	}

	@Override
	public void get(String path, OutputStream outputStream) throws TransferException {
		try {
			ftpClient.retrieveFile(path, outputStream);
			resolve();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00009, e);
		}
	}

	@Override
	public InputStream get(String path) throws TransferException {
		try {
			InputStream in = ftpClient.retrieveFileStream(path);
			resolve();
			if(ftpClient.completePendingCommand()){
				return in;
			}
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00009, e);
		}
		return null;
	}

	@Override
	public void put(String path, InputStream fileInputStream) throws TransferException {
		try {
			ftpClient.storeFile(path, fileInputStream);
			resolve();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00010, e);
		}
	}

	@Override
	public void delete(String path) throws TransferException {
		try {
			ftpClient.deleteFile(path);
			resolve();
		} catch (Exception e) {
			errorHandler(TransferCodeEnum.E120_00010, e);
		}
	}

	private void doLogout() {
		try {
			if (Objects.nonNull(ftpClient)) {
				ftpClient.logout();
			}
		} catch (Exception e) {
			log.warn("Try Logout Failed");
		}
	}

	private void doDisconnect() {
		try {
			if (Objects.nonNull(ftpClient)) {
				ftpClient.disconnect();
			}
		} catch (Exception e) {
			log.warn("Try Disconnect Failed");
		}
	}

	private void setConnectMode() {
		if ("ACTIVE".equalsIgnoreCase(this.connectionType)) {
			ftpClient.enterLocalActiveMode();
			log.info("Connection Mode : Active");
		} else {
			ftpClient.enterLocalPassiveMode();
			log.info("Connection Mode : Passive");
		}
	}

	private void setTransferMode() throws IOException, TransferException {
		if ("ASCII".equalsIgnoreCase(this.transferType)) {
			ftpClient.setFileType(FTPClient.ASCII_FILE_TYPE);
			log.info("TransferType : ASCII ");
		} else {
			ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE);
			log.info("TransferType : BINARY ");
		}
		resolve();
	}

	private List<String> splitPath(String path) {
		List<String> list = new ArrayList<String>();
		String prefix = FileNameUtils.getPrefix(path);
		String tempPath = FileNameUtils.replaceSeparatorNoEnd(path);
		StringJoiner join = new StringJoiner("/", prefix, "");
		for (String dir : FileNameUtils.splitSeparator(tempPath)) {
			if (prefix.startsWith(dir)) {
				continue;
			}
			String temp = join.add(dir).toString();
			if (Arrays.asList("", ".", "..", "/").contains(temp)) {
				continue;
			}
			list.add(temp);
		}
		return list;
	}

	private void resolve() throws TransferException {
		Integer replyCode = ftpClient.getReplyCode();
		String[] replyString = ftpClient.getReplyStrings();
		log.info("Reply Code : {} ", String.join(",", replyString));
		if (Objects.nonNull(replyCode) && (FTPReply.isNegativeTransient(replyCode) || FTPReply.isNegativePermanent(replyCode) || replyCode > 700)) {
			throw new TransferException(FtpCodeEnum.find(replyCode), String.join(",", replyString));
		}
	}

	private void errorHandler(MessageCode<?> code, Throwable t) throws TransferException {
		if (t instanceof TransferException) {
			throw (TransferException) t;
		}
		if (t instanceof ConnectException || t instanceof SocketTimeoutException) {
			throw new TransferException(TransferCodeEnum.E120_00000, t);
		}
		if (t instanceof SocketException) {
			throw new TransferException(TransferCodeEnum.E120_00001, t);
		}
		if (t instanceof FTPConnectionClosedException) {
			throw new TransferException(TransferCodeEnum.E120_00002, t);
		}
		if (t instanceof MalformedServerReplyException) {
			throw new TransferException(TransferCodeEnum.E120_00013, t);
		}
		if (t instanceof IOException) {
			throw new TransferException(TransferCodeEnum.E120_00005, t);
		}
		throw new TransferException(code, t);
	}

	/**
	 * 忽略副檔名及檔名大小寫
	 *
	 * @param fileName
	 * @return
	 */
	private static FTPFileFilter filterIgnoreExtension(final String fileName) {
		final String baseName = FileNameUtils.getBaseName(fileName);
		return (FTPFile file) -> {
			if (file.isFile()) {
				String remoteFileName = file.getName();
				String remoteFileBaseName = FileNameUtils.getBaseName(file.getName());
				return fileName.equalsIgnoreCase(remoteFileName) || baseName.equalsIgnoreCase(remoteFileBaseName);
			}
			return false;
		};
	}

}
