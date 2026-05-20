package com.fsap.monitor.sftp;

public enum ProcessStatusEnum {

	/** 成功 */
	SUCCESS("s"),

	/** 失敗 */
	ERROR("f");

	final String status;

	ProcessStatusEnum(String status) {
		this.status = status;
	}

	public String getStatus() {
		return this.status;
	}

	public static String success() {
		return ProcessStatusEnum.SUCCESS.getStatus();
	}

	public static String error() {
		return ProcessStatusEnum.ERROR.getStatus();
	}

}
