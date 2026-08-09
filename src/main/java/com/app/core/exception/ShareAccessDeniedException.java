package com.app.core.exception;

public class ShareAccessDeniedException extends AppException {
    public ShareAccessDeniedException(String message) {
        super(ErrorCode.SHARE_ACCESS_DENIED);
    }
}