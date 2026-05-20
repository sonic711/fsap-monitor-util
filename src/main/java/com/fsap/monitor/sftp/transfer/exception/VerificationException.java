package com.fsap.monitor.sftp.transfer.exception;

import java.util.List;

public class VerificationException extends BusinessException {

	private static final long serialVersionUID = -2833312092956697959L;

	public VerificationException(String msg) {
		super(msg);
	}

	public VerificationException(MessageCode<?> code) {
		super(code);
	}

	public VerificationException(MessageCode<?> code, String supplemental) {
		super(code, supplemental);
	}

	public VerificationException(MessageCode<?> code, List<String> supplemental) {
		super(code, supplemental);
	}

	public VerificationException(MessageCode<?> code, Throwable t) {
		super(code, t);
	}

	public VerificationException(String msg, Throwable t) {
		super(msg, t);
	}
}
