package com.fsap.monitor.sftp.vo;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileInfoVO implements Serializable {
	private static final long serialVersionUID = -686128173128136726L;
	private String filePath; // 檔案路徑
	private String fileName; // 檔案名稱
	private byte[] content; // 檔案內容
	private String fileId;// 連線分類
}
