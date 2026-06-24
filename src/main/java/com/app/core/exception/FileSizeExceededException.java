package com.app.core.exception;

public class FileSizeExceededException extends AppException {

    public FileSizeExceededException() {
        super(ErrorCode.FILE_SIZE_EXCEEDED);
    }
}