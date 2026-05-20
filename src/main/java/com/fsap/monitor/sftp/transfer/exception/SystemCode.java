package com.fsap.monitor.sftp.transfer.exception;

public enum SystemCode implements MessageCode<SystemCode> {

	/** 成功 */
	S0000000(""),
	E0000000("查無資料"),
	E0000001("處理失敗"),
	E0000002("重覆登入"),
	E0000003("交易暫禁"),
	E0999999("例外錯誤");

	private String defaultMessage;

	SystemCode(String defaultMessage) {
		this.defaultMessage = defaultMessage;
	}

	@Override
	public SystemCode getEnum() {
		return this;
	}

	@Override
	public String getCode() {
		return this.name();
	}

	@Override
	public String getMessage() {
		return defaultMessage;
	}

	@Override
	public String getType() {
		return null;
	}

}
