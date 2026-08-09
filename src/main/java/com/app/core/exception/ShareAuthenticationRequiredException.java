package com.app.core.exception;

public class ShareAuthenticationRequiredException extends AppException {
    public ShareAuthenticationRequiredException(String message) {
        super(ErrorCode.SHARE_AUTHENTICATION_REQUIRED);
    }
}