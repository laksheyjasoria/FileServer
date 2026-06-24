package com.app.core.exception;

public class InvalidShareException extends AppException {

    public InvalidShareException() {
        super(ErrorCode.INVALID_SHARE, "Invalid or expired share");
    }
}