package com.app.core.exception;

public class InvalidCredentialsException extends AppException {
	public InvalidCredentialsException() {
		super(ErrorCode.INVALID_CREDENTIALS);
	}
}