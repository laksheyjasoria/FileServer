package com.app.core.exception;

public class FileNotFoundException extends AppException {

    public FileNotFoundException() {
        super(ErrorCode.FILE_NOT_FOUND, "File not found");
    }
}