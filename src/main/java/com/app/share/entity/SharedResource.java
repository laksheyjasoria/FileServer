package com.app.share.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "shared_resources")
public class SharedResource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String token;

    private String fileId;

    private String createdBy;

    private boolean publicAccess;

    private String password;

    private LocalDateTime expiry;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getFileId() {
        return fileId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public String getPassword() {
        return password;
    }

    public LocalDateTime getExpiry() {
        return expiry;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setExpiry(LocalDateTime expiry) {
        this.expiry = expiry;
    }
}