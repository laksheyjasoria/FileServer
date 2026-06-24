package com.app.core.exception;

public class LoggerNotFoundException extends AppException {

	public LoggerNotFoundException() {
		super(ErrorCode.LOGGER_NOT_FOUND, "Logger not found");
	}
}