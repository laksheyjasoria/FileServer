package com.app.core.exception;

public class InvalidShareException extends AppException {
    public InvalidShareException() {
        super(ErrorCode.SHARE_NOT_FOUND); // or use a custom code
    }
}