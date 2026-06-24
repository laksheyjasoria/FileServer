package com.app.billing.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    @ManyToOne(fetch = FetchType.EAGER)
    private Plan plan;

    private LocalDateTime startDate;

    private LocalDateTime expiryDate;

    private boolean active;

    @PrePersist
    public void prePersist() {

        if (startDate == null) {
            startDate = LocalDateTime.now();
        }

        if (!active) {
            active = true;
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public Plan getPlan() {
        return plan;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getExpiryDate() {
        return expiryDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public void setExpiryDate(LocalDateTime expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}