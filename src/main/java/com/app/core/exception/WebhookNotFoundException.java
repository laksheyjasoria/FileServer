package com.app.core.exception;

public class WebhookNotFoundException extends AppException {

    public WebhookNotFoundException() {
        super(ErrorCode.WEBHOOK_NOT_FOUND, "Webhook not found");
    }
}