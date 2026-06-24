package com.app.core.exception;

public class UserAlreadyExistsException extends AppException {
	public UserAlreadyExistsException() {
		super(ErrorCode.USER_ALREADY_EXISTS);
	}
}