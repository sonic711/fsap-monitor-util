package com.fsap.monitor.sftp.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fsap.monitor.sftp.ProcessStatusEnum;
import com.fsap.monitor.sftp.config.EncryptConfiguration;
import com.fsap.monitor.sftp.transfer.FileEntry;
import com.fsap.monitor.sftp.transfer.FileTransfer;
import com.fsap.monitor.sftp.transfer.FileTransferUtils;
import com.fsap.monitor.sftp.transfer.exception.TransferException;
import com.fsap.monitor.sftp.vo.BasicOut;
import com.fsap.monitor.sftp.vo.FileInfoVO;
import com.fsap.monitor.sftp.vo.RemoteInfo;

@Service
public class FileService {

	private Logger log = LoggerFactory.getLogger(getClass());

	private final RemoteInfo remoteInfo;

	@Autowired
	FileService(RemoteInfo remoteInfo) {
		this.remoteInfo = remoteInfo;
		this.remoteInfo.setCode(EncryptConfiguration.decrypt(this.remoteInfo.getCode()));
	}

	/**
	 * 下載檔案
	 *
	 * @param fileInfoVO
	 * @return
	 */
	public BasicOut<FileInfoVO> download(FileInfoVO fileInfoVO) {
		BasicOut<FileInfoVO> result = new BasicOut<FileInfoVO>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();
			if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
				transfer.cd(fileInfoVO.getFilePath());
			} else {
				transfer.cd(remoteInfo.getDefPath());
			}
			// (忽略檔名大小寫及副檔名)
			List<FileEntry> fileList = transfer.lsIgnoreExtension(fileInfoVO.getFileName());
			if (fileList.isEmpty()) {
				result.setStatus(ProcessStatusEnum.ERROR.getStatus());
				result.addMessage("File Not Found");
			} else {
				FileEntry fileEntry = fileList.stream().findFirst().get();
				if (fileList.size() > 1) {
					log.info("Similar File Name [{}] , Get First File [{}]", fileList.stream().map(FileEntry::getFileName).collect(Collectors.joining(",")), fileEntry.getFileName());
				}
				try (InputStream is = transfer.get(fileEntry.getFileName())) {
					fileInfoVO.setContent(IOUtils.toByteArray(is));
					result.setData(fileInfoVO);
					result.setStatus(ProcessStatusEnum.SUCCESS.getStatus());
				}
			}
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (IOException e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 上傳檔案
	 *
	 * @param fileInfoVO
	 * @return
	 */
	public BasicOut<FileInfoVO> upload(FileInfoVO fileInfoVO) {
		BasicOut<FileInfoVO> result = new BasicOut<FileInfoVO>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();
			if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
				transfer.cdWithMkdir(fileInfoVO.getFilePath());
			} else {
				transfer.cdWithMkdir(remoteInfo.getDefPath());
			}
			try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileInfoVO.getContent())) {
				transfer.put(fileInfoVO.getFileName(), inputStream);
				result.setData(fileInfoVO);
				result.setStatus(ProcessStatusEnum.SUCCESS.getStatus());
			}
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (IOException e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 刪除檔案
	 *
	 * @param fileInfoVO
	 * @return
	 */
	public BasicOut<FileInfoVO> delete(FileInfoVO fileInfoVO) {
		BasicOut<FileInfoVO> result = new BasicOut<FileInfoVO>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();
			if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
				transfer.cd(fileInfoVO.getFilePath());
			} else {
				transfer.cd(remoteInfo.getDefPath());
			}
			transfer.delete(fileInfoVO.getFileName());
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 檢查檔案是否存在
	 *
	 * @param fileInfoVO
	 * @return
	 */
	public BasicOut<Boolean> checkFile(FileInfoVO fileInfoVO) {
		BasicOut<Boolean> result = new BasicOut<Boolean>();
		result.success();
		result.setData(Boolean.TRUE);
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();
			if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
				transfer.cd(fileInfoVO.getFilePath());
			} else {
				transfer.cd(remoteInfo.getDefPath());
			}
			// (忽略檔名大小寫及副檔名)
			List<FileEntry> fileList = transfer.lsIgnoreExtension(fileInfoVO.getFileName());
			if (fileList.isEmpty()) {
				result.setData(Boolean.FALSE);
			}
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 查詢多檔案是否有存在
	 *
	 * @param fileInfoVO
	 * @return
	 * @throws Exception
	 */
	public BasicOut<List<String>> checkFileList(FileInfoVO fileInfoVO, List<String> fileNameList) {
		BasicOut<List<String>> result = new BasicOut<List<String>>();
		List<String> checkFileList = new ArrayList<>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();
			if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
				transfer.cd(fileInfoVO.getFilePath());
			} else {
				transfer.cd(remoteInfo.getDefPath());
			}
			// (忽略檔名大小寫及副檔名)
			for (String fileName : fileNameList) {
				List<FileEntry> fileList = transfer.lsIgnoreExtension(fileName);
				if (!fileList.isEmpty()) {
					if (fileList.size() > 1) {
						log.info("Similar File Name [{}] , Get First File [{}]", fileList.stream().map(FileEntry::getFileName).collect(Collectors.joining(",")), fileName);
					} else {
						checkFileList.add(fileName);
					}
				}
			}
			result.setData(checkFileList);
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 下載多筆檔案
	 *
	 * @param fileInfoVOList
	 * @return
	 */
	public BasicOut<List<FileInfoVO>> downloads(List<FileInfoVO> fileInfoVOList) {
		BasicOut<List<FileInfoVO>> result = new BasicOut<>();
		List<FileInfoVO> fileVOList = new ArrayList<>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();

			for (FileInfoVO fileInfoVO : fileInfoVOList) {
				transfer.cd("/");
				if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
					transfer.cd(fileInfoVO.getFilePath());
				} else {
					transfer.cd(remoteInfo.getDefPath());
				}

				// (不忽略檔名大小寫及副檔名)
				List<FileEntry> fileList = transfer.ls(fileInfoVO.getFileName());
				if (fileList.isEmpty()) {
					result.addMessage("File Not Found");
				} else {
					FileEntry fileEntry = fileList.stream().findFirst().get();
					if (fileList.size() > 1) {
						log.info("Similar File Name [{}] , Get First File [{}]", fileList.stream().map(FileEntry::getFileName).collect(Collectors.joining(",")), fileEntry.getFileName());
					}
					try (InputStream is = transfer.get(fileEntry.getFileName())) {
						fileInfoVO.setContent(IOUtils.toByteArray(is));
						fileVOList.add(fileInfoVO);
					}
				}
			}
			result.setData(fileVOList);
			result.setStatus(ProcessStatusEnum.SUCCESS.getStatus());
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (IOException e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 下載指定目錄下符合檔名的檔案至local tempFilePath
	 * 不檢查目錄是否存在
	 *
	 * @param fileInfoVOs  要下載的檔案集合
	 * @param tempFilePath local暫存目錄
	 * @return
	 */
	public BasicOut<List<FileInfoVO>> downloadToLocalNoCheck(List<FileInfoVO> fileInfoVOs, String tempFilePath) {
		BasicOut<List<FileInfoVO>> result = new BasicOut<>();

		List<FileInfoVO> successFiles = new ArrayList<>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();

			for (FileInfoVO fileInfoVO : fileInfoVOs) {
				transfer.cd("/");
				if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
					transfer.cdWithMkdir(fileInfoVO.getFilePath());
					log.debug("downloadToLocal file from [{}]", fileInfoVO.getFilePath());
				} else {
					transfer.cdWithMkdir(remoteInfo.getDefPath());
					log.debug("downloadToLocal file from [{}]", remoteInfo.getDefPath());
				}

				Optional<FileEntry> fileEntry = transfer.ls().stream()//
						.filter(FileEntry::isFile)//
						.filter(f -> f.getFileName().equals(fileInfoVO.getFileName())).findFirst();// 只要符合檔名檔案
				if (fileEntry.isPresent() && fileEntry.get().isFile()) {
					FileEntry file = fileEntry.get();
					File dir = new File(tempFilePath);// 如果local目錄不存在，則建立
					if (!dir.exists()) {
						log.debug("mkdirSuccess [{}] {}", tempFilePath, dir.mkdirs() ? "success" : "failed");
					}
					File localFile = new File(tempFilePath + "/" + file.getFileName());
					log.debug("[{}] download file to [{}]", file.getFileName(), localFile.getAbsolutePath());
					try (FileOutputStream fileOutputStream = new FileOutputStream(localFile)) {
						transfer.get(file.getFileName(), fileOutputStream);
						successFiles.add(fileInfoVO);
					}
				} else {
					log.debug("File Not Found [{}]", fileInfoVO.getFileName());
					result.setStatus(ProcessStatusEnum.ERROR.getStatus());
					result.addMessage("File Not Found");
				}
				result.setData(successFiles);
			}
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage(), e.getCode().getCode()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Collections.singletonList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 上傳多筆 檔案
	 *
	 * @param fileInfoVO
	 * @return
	 */
	public BasicOut<List<FileInfoVO>> uploads(String fileId, List<FileInfoVO> fileInfoVOList) {
		BasicOut<List<FileInfoVO>> result = new BasicOut<List<FileInfoVO>>();
		List<FileInfoVO> fileVOList = new ArrayList<FileInfoVO>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();
			for (FileInfoVO fileInfoVO : fileInfoVOList) {
				transfer.cd("/");
				if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
					transfer.cd(fileInfoVO.getFilePath());
				} else {
					transfer.cd(remoteInfo.getDefPath());
				}
				try (ByteArrayInputStream inputStream = new ByteArrayInputStream(fileInfoVO.getContent())) {
					transfer.put(fileInfoVO.getFileName(), inputStream);
					fileVOList.add(fileInfoVO);
				}
			}
			result.setData(fileVOList);
			result.setStatus(ProcessStatusEnum.SUCCESS.getStatus());
		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (IOException e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

	/**
	 * 刪除多筆檔案
	 *
	 * @param fileInfoVO
	 * @return
	 */
	public BasicOut<List<FileInfoVO>> deletes(String fileId, List<FileInfoVO> fileInfoVOList) {
		BasicOut<List<FileInfoVO>> result = new BasicOut<List<FileInfoVO>>();
		try (FileTransfer transfer = FileTransferUtils.newInstance(remoteInfo)) {
			transfer.connection();

			for (FileInfoVO fileInfoVO : fileInfoVOList) {
				transfer.cd("/");
				if (StringUtils.isNotBlank(fileInfoVO.getFilePath())) {
					transfer.cd(fileInfoVO.getFilePath());
				} else {
					transfer.cd(remoteInfo.getDefPath());
				}
				transfer.delete(fileInfoVO.getFileName());
			}

		} catch (TransferException e) {
			log.error("檔案傳輸異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		} catch (Exception e) {
			log.error("檔案存取異常", e);
			result.setStatus(ProcessStatusEnum.ERROR.getStatus());
			result.setMessage(Arrays.asList(e.getLocalizedMessage()));
		}
		return result;
	}

}
