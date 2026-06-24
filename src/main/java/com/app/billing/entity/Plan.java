package com.app.billing.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String name;

    private Long storageLimitBytes;

    private Long maxUploadSizeBytes;

    private Integer dailyUploadLimit;

    private Integer apiRequestLimit;

    private BigDecimal price;

    private boolean active;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Long getStorageLimitBytes() {
        return storageLimitBytes;
    }

    public Long getMaxUploadSizeBytes() {
        return maxUploadSizeBytes;
    }

    public Integer getDailyUploadLimit() {
        return dailyUploadLimit;
    }

    public Integer getApiRequestLimit() {
        return apiRequestLimit;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStorageLimitBytes(Long storageLimitBytes) {
        this.storageLimitBytes = storageLimitBytes;
    }

    public void setMaxUploadSizeBytes(Long maxUploadSizeBytes) {
        this.maxUploadSizeBytes = maxUploadSizeBytes;
    }

    public void setDailyUploadLimit(Integer dailyUploadLimit) {
        this.dailyUploadLimit = dailyUploadLimit;
    }

    public void setApiRequestLimit(Integer apiRequestLimit) {
        this.apiRequestLimit = apiRequestLimit;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}