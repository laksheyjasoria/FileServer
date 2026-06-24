package com.app.core.exception;

public class OAuthException extends AppException {

    public OAuthException() {
        super(ErrorCode.OAUTH_ERROR);
    }

    public OAuthException(String message) {
        super(ErrorCode.OAUTH_ERROR, message);
    }
}