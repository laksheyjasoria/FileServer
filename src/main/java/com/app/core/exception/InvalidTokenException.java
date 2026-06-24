package com.app.core.exception;

public class InvalidTokenException extends AppException {
	public InvalidTokenException() {
		super(ErrorCode.INVALID_TOKEN);
	}
}