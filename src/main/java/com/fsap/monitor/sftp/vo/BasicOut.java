package com.fsap.monitor.sftp.vo;

import java.beans.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.fsap.monitor.sftp.CommonApiCode;
import com.fsap.monitor.sftp.ProcessStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BasicOut<T> implements Serializable {
	@Default
	private int code = CommonApiCode.SUCCESS.getCode();
	@Default
	private String status = ProcessStatusEnum.SUCCESS.getStatus();
	@Default
	private List<String> message = new ArrayList<>();

	private T data;

	private PageInfo pageInfo;

	public BasicOut(int pageIndex, int pageSize, int totalRows) {
		this.pageInfo = new PageInfo();
		this.pageInfo.setPageSize(pageSize);
		this.pageInfo.setTotalRows(totalRows);
		int totalPages = totalRows % pageSize != 0 ? totalRows / pageSize + 1 : totalRows / pageSize;
		this.pageInfo.setTotalPages(totalPages);
		this.pageInfo.setPageIndex(pageIndex > totalPages ? totalPages : pageIndex);
	}

	public void addMessage(String msg) {
		if (message == null) {
			this.message = Collections.singletonList(msg);
		} else {
			this.message.add(msg);
		}
	}

	public BasicOut<T> success() {
		this.code = CommonApiCode.SUCCESS.getCode();
		this.message = Arrays.asList(CommonApiCode.SUCCESS.getMessage());
		this.status = ProcessStatusEnum.success();
		return this;
	}

	public BasicOut<T> success(T data) {
		this.code = CommonApiCode.SUCCESS.getCode();
		this.message = Arrays.asList(CommonApiCode.SUCCESS.getMessage());
		this.data = data;
		this.status = ProcessStatusEnum.success();
		return this;
	}

	public BasicOut<T> success(CommonApiCode code, String message) {
		this.code = CommonApiCode.SUCCESS.getCode();
		this.message = Arrays.asList(CommonApiCode.SUCCESS.getMessage());
		this.status = ProcessStatusEnum.success();
		return this;
	}

	public BasicOut<T> error() {
		this.code = CommonApiCode.SYSTEM_EXCEPTION.getCode();
		this.status = ProcessStatusEnum.ERROR.getStatus();
		this.message = Collections.singletonList(CommonApiCode.SYSTEM_EXCEPTION.getMessage());
		return this;
	}

	public BasicOut<T> error(CommonApiCode code) {
		this.code = code.getCode();
		this.status = ProcessStatusEnum.ERROR.getStatus();
		this.message = Collections.singletonList(code.getMessage());
		return this;
	}

	public BasicOut<T> error(CommonApiCode code, List<String> messages) {
		this.code = code.getCode();
		this.status = ProcessStatusEnum.ERROR.getStatus();
		this.message = messages;
		return this;
	}

	public BasicOut<T> error(List<String> messages) {
		this.code = CommonApiCode.SYSTEM_EXCEPTION.getCode();
		this.status = ProcessStatusEnum.ERROR.getStatus();
		this.message = messages;
		return this;
	}

	@Transient
	public boolean isProcessSuccess() {
		return ProcessStatusEnum.SUCCESS.getStatus().equalsIgnoreCase(this.status);
	}

}
