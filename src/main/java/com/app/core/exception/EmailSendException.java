package com.app.core.exception;

public class EmailSendException extends AppException {

	public EmailSendException() {
		super(ErrorCode.EMAIL_SEND_FAILED);
	}

	public EmailSendException(String message) {
		super(ErrorCode.EMAIL_SEND_FAILED, message);
	}
}