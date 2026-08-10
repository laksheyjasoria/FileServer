package com.app.core.exception;

public class ShareExpiredException extends AppException {
    public ShareExpiredException() {
        super(ErrorCode.SHARE_EXPIRED);
    }
}