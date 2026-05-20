package com.fsap.monitor.sftp;

import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CommonApiCode {

	/** 20000 成功 */
	SUCCESS(20000, "success"),
	/** 50000 未知異常 */
	SYSTEM_EXCEPTION(50000, "未知異常"),
	/** 50001 登入憑證無效 */
	JWT_TOKEN_VALIDATE_ERROR(50001, "登入憑證無效，請重新登入"),
	/** 50003 登入憑證過期 */
	ACCESS_TOKEN_EXPIRED(50003, "登入憑證過期，請重新登入"),
	/** 50004 使用者不存在 */
	INVALID_USER(50004, "使用者不存在"),
	/** 50005 登入資訊錯誤 */
	USERNAME_PASSWORD_WRONG(50005, "登入資訊錯誤"),
	/** 50006 AD驗證失敗 */
	AD_VERIFICATION_FAILED(50006, "AD 驗證失敗"),
	/** 50007 查無使用者權限 */
	USER_ROLE_UNDEFINED(50007, "查無使用者權限"),
	/** 50008 查無使用者所屬單位資訊 */
	USER_UNIT_UNDEFINED(50008, "查無使用者所屬單位資訊"),
	/** 1023 此角色無執行該功能權限 */
	NO_AUTHORITY(1023, "此角色無執行該功能權限"),
	/** 99999 客戶端網頁程式版本過舊，請重新登入 */
	SYSTEM_VERSION_ERROR(99999, "客戶端網頁程式版本過舊，請重新登入"),
	/** 1010 No Such File */
	NO_SUCH_FILE(1010, "查無指定檔案");

	private final int code;

	private final String message;

	public static CommonApiCode findByCode(int code) {
		return Stream.of(CommonApiCode.values()).filter(d -> d.code == code).findFirst().orElse(null);
	}
}
