package com.app.core.exception;

public class ShareRemovedException extends AppException {

    public ShareRemovedException() {
        super(ErrorCode.SHARE_REMOVED);
    }

    public ShareRemovedException(String message) {
        super(ErrorCode.SHARE_REMOVED, message);
    }
}