package com.fsap.monitor.sftp.transfer.exception;

import java.util.ArrayList;
import java.util.List;

public class BusinessException extends RuntimeException implements ApplicationException {

	private static final long serialVersionUID = 8385967837323014309L;

	/** 系統代碼 */
	private MessageCode<?> code = SystemCode.E0999999;
	/** 額外錯誤訊息 */
	private List<String> supplemental = new ArrayList<String>();

	public BusinessException(String msg) {
		super(msg);
	}

	public BusinessException(MessageCode<?> code) {
		super(code.getMessage());
		this.code = code;
	}

	public BusinessException(MessageCode<?> code, String supplemental) {
		super(supplemental);
		this.code = code;
		this.supplemental.add(supplemental);
	}

	public BusinessException(MessageCode<?> code, List<String> supplemental) {
		super(String.join(",", supplemental));
		this.code = code;
		this.supplemental.addAll(supplemental);
	}

	public BusinessException(MessageCode<?> code, Throwable t) {
		super(code.getMessage(), t);
		this.code = code;
		this.supplemental.add(t.getLocalizedMessage());
	}

	public BusinessException(String msg, Throwable t) {
		super(msg, t);
		this.supplemental.add(t.getLocalizedMessage());
	}

	@Override
	public MessageCode<?> getCode() {
		return code;
	}

	@Override
	public List<String> getSupplemental() {
		return supplemental;
	}

}
