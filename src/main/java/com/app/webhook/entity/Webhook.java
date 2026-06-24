package com.app.webhook.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "webhooks")
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    private String url;

    private String secret;

    private boolean enabled;

    private Integer retryCount;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();

        if (retryCount == null) {
            retryCount = 0;
        }

        enabled = true;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getUrl() {
        return url;
    }

    public String getSecret() {
        return secret;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }
}