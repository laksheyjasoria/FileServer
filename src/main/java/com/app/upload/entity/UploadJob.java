package com.app.upload.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "upload_jobs")
public class UploadJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;

    private String fileName;

    private Long totalSize;

    private Integer totalChunks;

    /**
     * Server-authoritative logical chunk size in bytes.
     */
    private Integer chunkSize;

    private Integer uploadedChunks;

    /**
     * Server-authoritative number of successfully stored bytes.
     */
    private Long uploadedBytes;

    @Enumerated(EnumType.STRING)
    private UploadStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (uploadedChunks == null) {
            uploadedChunks = 0;
        }

        if (uploadedBytes == null) {
            uploadedBytes = 0L;
        }

        if (status == null) {
            status = UploadStatus.IN_PROGRESS;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Integer getUploadedChunks() {
        return uploadedChunks;
    }

    public void setUploadedChunks(Integer uploadedChunks) {
        this.uploadedChunks = uploadedChunks;
    }

    public Long getUploadedBytes() {
        return uploadedBytes;
    }

    public void setUploadedBytes(Long uploadedBytes) {
        this.uploadedBytes = uploadedBytes;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}