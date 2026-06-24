package com.app.core.exception;

public class StorageLimitExceededException extends AppException {

    public StorageLimitExceededException() {
        super(ErrorCode.STORAGE_LIMIT_EXCEEDED,
                "Storage limit exceeded");
    }
}