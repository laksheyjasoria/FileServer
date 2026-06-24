package com.app.core.exception;

public class StorageException extends AppException {

    public StorageException(String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }
}