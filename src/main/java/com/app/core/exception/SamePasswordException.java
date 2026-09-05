package com.app.core.exception;

public class SamePasswordException extends AppException {

    public SamePasswordException() {
        super(ErrorCode.SAME_PASSWORD);
    }

    public SamePasswordException(String customMessage) {
        super(ErrorCode.SAME_PASSWORD, customMessage);
    }
}