package com.app.core.exception;

public class SharePasswordRequiredException extends AppException {

    public SharePasswordRequiredException() {
        super(ErrorCode.SHARE_PASSWORD_REQUIRED, "Share password required");
    }
}