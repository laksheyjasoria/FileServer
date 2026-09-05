package com.app.core.exception;

public class InvalidPasswordException extends AppException {

    public InvalidPasswordException() {
        super(ErrorCode.INVALID_PASSWORD);
    }

    public InvalidPasswordException(String customMessage) {
        super(ErrorCode.INVALID_PASSWORD, customMessage);
    }
}