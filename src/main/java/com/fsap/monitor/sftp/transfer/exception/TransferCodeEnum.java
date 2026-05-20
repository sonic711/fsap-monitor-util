package com.fsap.monitor.sftp.transfer.exception;

public enum TransferCodeEnum implements MessageCode<TransferCodeEnum> {
	E120_00000("連線逾時"),
	E120_00001("連線失敗"),
	E120_00002("連線已中斷"),
	E120_00003("登出失敗"),
	E120_00004("關閉連線失敗"),
	E120_00005("連線異常"),
	E120_00006("讀取路徑失敗"),
	E120_00007("讀取檔案列表失敗"),
	E120_00008("切換目錄失敗"),
	E120_00009("下載檔案失敗"),
	E120_00010("上傳檔案異常"),
	E120_00011("刪除檔案異常"),
	E120_00012("建立連線失敗"),
	E120_00013("無法解析回應代碼"),
	E120_00014("建立目錄失敗"),
	//
	E120_99999("未知異常");

	private String defaultMessage;

	TransferCodeEnum(String defaultMessage) {
		this.defaultMessage = defaultMessage;
	}

	@Override
	public TransferCodeEnum getEnum() {
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
		return "TransferCode";
	}

}
