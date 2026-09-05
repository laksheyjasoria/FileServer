package com.app.core.exception;

public class InvalidCredentialsException extends AppException {
	public InvalidCredentialsException() {
		super(ErrorCode.INVALID_CREDENTIALS);
	}

	public InvalidCredentialsException(String customMessage) {
		super(ErrorCode.INVALID_CREDENTIALS, customMessage);
	}
}