package com.app.core.exception;

public class UploadNotFoundException extends AppException {

    public UploadNotFoundException() {
        super(ErrorCode.UPLOAD_NOT_FOUND, "Upload job not found");
    }
}