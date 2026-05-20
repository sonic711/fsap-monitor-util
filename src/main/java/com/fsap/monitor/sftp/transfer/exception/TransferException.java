package com.fsap.monitor.sftp.transfer.exception;

import java.util.ArrayList;
import java.util.List;

public class TransferException extends Exception implements ApplicationException {

	private static final long serialVersionUID = -7517986285699988163L;

	/** 系統代碼 */
	private MessageCode<?> code = SystemCode.S0000000;
	/** 額外錯誤訊息 */
	private List<String> supplemental = new ArrayList<String>();

	public TransferException(String msg) {
		super(msg);
	}

	public TransferException(Throwable t) {
		super(t);
	}

	public TransferException(MessageCode<?> code) {
		super(code.getMessage());
		this.code = code;
	}

	public TransferException(MessageCode<?> code, String supplemental) {
		super(supplemental);
		this.code = code;
		this.supplemental.add(supplemental);
	}

	public TransferException(MessageCode<?> code, List<String> supplemental) {
		super(String.join(",", supplemental));
		this.code = code;
		this.supplemental.addAll(supplemental);
	}

	public TransferException(MessageCode<?> code, Throwable t) {
		super(t);
		this.code = code;
		this.supplemental.add(t.getLocalizedMessage());
	}

	public TransferException(String msg, Throwable t) {
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
