package com.app.core.exception;

public class ShareNotFoundException extends AppException {
    public ShareNotFoundException() {
        super(ErrorCode.SHARE_NOT_FOUND);
    }
}