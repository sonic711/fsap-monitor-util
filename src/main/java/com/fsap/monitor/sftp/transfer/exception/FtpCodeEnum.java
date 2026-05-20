package com.fsap.monitor.sftp.transfer.exception;

public enum FtpCodeEnum implements MessageCode<FtpCodeEnum> {

	S121_00110(110, "重新啟動標記回覆"),
	S121_00120(120, "服務就緒"),
	S121_00125(125, "資料連線已經開啟;傳輸開始"),
	S121_00150(150, "檔案狀態無誤;將開啟資料連線"),
	S121_00200(200, "操作已執行完成"),
	S121_00211(211, "系統狀態"),
	S121_00212(212, "目錄狀態"),
	S121_00213(213, "檔案狀態"),
	S121_00214(214, "說明訊息"),
	S121_00215(215, "名稱系統型別"),
	S121_00220(220, "Service ready for new user"),
	S121_00221(221, "Service closing control connection"),
	S121_00225(225, "Data connection open"),
	S121_00226(226, "Closing data connection"),
	S121_00227(227, "Entering Passive Mode"),
	S121_00228(228, "Entering Long Passive Mode"),
	S121_00229(229, "Entering Extended Passive Mode"),
	S121_00230(230, "User logged in, proceed. Logged out if appropriate."),
	S121_00231(231, "已登出，操作中斷"),
	S121_00232(232, "Logout command noted, will complete when transfer done."),
	S121_00234(234, "安全性資料已交換完成"),
	S121_00250(250, "已完成檔案異動操作"),
	S121_00257(257, "已建立路徑"),
	W121_00331(331, "帳號正確，需輸入密碼"),
	W121_00332(332, "需要登入"),
	W121_00350(350, "檔案存取操作中斷"),
	E121_00421(421, "服務無法使用"),
	E121_00425(425, "無法開啟資料連接"),
	E121_00426(426, "連接已關閉;傳輸中止"),
	E121_00430(430, "帳號或密碼無效"),
	E121_00434(434, "主機無法連線操作"),
	E121_00450(450, "檔案無法使用"),
	E121_00451(451, "系統錯誤"),
	E121_00452(452, "系統中沒有足夠的儲存空間"),
	E121_00500(500, "語法錯誤，無法辨認的命令"),
	E121_00501(501, "參數錯誤"),
	E121_00502(502, "操作指令未執行"),
	E121_00503(503, "操作指令順序錯誤"),
	E121_00504(504, "無法執行操作指令"),
	E121_00530(530, "未登入"),
	E121_00532(532, "需要帳戶以儲存檔案"),
	E121_00534(534, "無法連線，需啟用SSL"),
	E121_00550(550, "請求無法執行，無法操作(找不到檔案/目錄或沒有存取權)"),
	E121_00551(551, "頁面類型不明"),
	E121_00552(552, "超過儲存配置"),
	E121_00553(553, "不允許的檔案名稱/無法建立檔案"),
	M121_00631(631, "完整性保護回覆"),
	M121_00632(632, "機密性和完整性保護回覆"),
	M121_00633(633, "機密性保護回覆"),
	E121_10000(10000, "連線錯誤"),
	E121_10054(10054, "遠端主機重置，連線關閉"),
	E121_10060(10060, "無法連線"),
	E121_10061(10061, "遠端主機拒絕連線"),
	E121_10066(10066, "目錄不得為空"),
	E121_10068(10068, "用戶連線數達上限"),
	W121_00999(-1, "未知狀態");

	public final int ftpResultCode;
	private String defaultMessage;

	FtpCodeEnum(int code, String defaultMessage) {
		this.ftpResultCode = code;
		this.defaultMessage = defaultMessage;
	}

	public static FtpCodeEnum find(int reply) {
		for (FtpCodeEnum c : values()) {
			if (c.ftpResultCode == reply) {
				return c;
			}
		}
		return W121_00999;
	}

	@Override
	public FtpCodeEnum getEnum() {
		return this;
	}

	@Override
	public String getCode() {
		return this.name();
	}

	@Override
	public String getMessage() {
		return this.defaultMessage;
	}

	@Override
	public String getType() {
		return "FtpCode";
	}
}
