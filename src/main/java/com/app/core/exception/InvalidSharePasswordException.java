package com.app.core.exception;

public class InvalidSharePasswordException extends AppException {

    public InvalidSharePasswordException() {
        super(ErrorCode.INVALID_SHARE_PASSWORD, "Invalid share password");
    }
}