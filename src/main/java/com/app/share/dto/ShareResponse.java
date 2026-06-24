package com.app.share.dto;

public class ShareResponse {

    private String url;
    private String token;

    public ShareResponse() {
    }

    public ShareResponse(String url, String token) {
        this.url = url;
        this.token = token;
    }

    public String getUrl() {
        return url;
    }

    public String getToken() {
        return token;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setToken(String token) {
        this.token = token;
    }
}