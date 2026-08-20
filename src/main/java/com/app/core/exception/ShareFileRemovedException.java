package com.app.core.exception;

public class ShareFileRemovedException extends AppException {

    public ShareFileRemovedException() {
        super(ErrorCode.SHARE_FILE_REMOVED);
    }

    public ShareFileRemovedException(String message) {
        super(ErrorCode.SHARE_FILE_REMOVED, message);
    }
}