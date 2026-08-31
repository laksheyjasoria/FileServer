package com.app.core.exception;

public class AccountDeactivatedException extends AppException {

    public AccountDeactivatedException() {
        super(ErrorCode.ACCOUNT_DEACTIVATED);
    }

    public AccountDeactivatedException(String customMessage) {
        super(ErrorCode.ACCOUNT_DEACTIVATED, customMessage);
    }
}