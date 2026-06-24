package com.app.webhook.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String webhookId;

    @Column(columnDefinition = "TEXT")
    private String payload;

    private boolean delivered;

    private Integer attempts;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();

        if (attempts == null) {
            attempts = 0;
        }
    }

    public String getId() {
        return id;
    }

    public String getWebhookId() {
        return webhookId;
    }

    public String getPayload() {
        return payload;
    }

    public boolean isDelivered() {
        return delivered;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setWebhookId(String webhookId) {
        this.webhookId = webhookId;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public void setDelivered(boolean delivered) {
        this.delivered = delivered;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }
}