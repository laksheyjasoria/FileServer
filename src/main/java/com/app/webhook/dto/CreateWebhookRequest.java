package com.app.webhook.dto;

public class CreateWebhookRequest {

    private String url;

    private String secret;

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}