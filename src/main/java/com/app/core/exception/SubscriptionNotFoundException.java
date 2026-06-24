package com.app.core.exception;

public class SubscriptionNotFoundException extends AppException {

    public SubscriptionNotFoundException() {
        super(ErrorCode.SUBSCRIPTION_NOT_FOUND, "Subscription not found");
    }
}