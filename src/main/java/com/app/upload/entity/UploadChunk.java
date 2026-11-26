package com.app.upload.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(
    name = "upload_chunks",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_upload_chunks_job_index",
            columnNames = {"upload_job_id", "chunk_index"}
        )
    }
)
public class UploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "upload_job_id", nullable = false)
    private String uploadJobId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    private String telegramFileId;

    private Long size;

    @Enumerated(EnumType.STRING)
    private UploadChunkStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (status == null) {
            status = UploadChunkStatus.COMPLETED;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUploadJobId() {
        return uploadJobId;
    }

    public void setUploadJobId(String uploadJobId) {
        this.uploadJobId = uploadJobId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getTelegramFileId() {
        return telegramFileId;
    }

    public void setTelegramFileId(String telegramFileId) {
        this.telegramFileId = telegramFileId;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public UploadChunkStatus getStatus() {
        return status;
    }

    public void setStatus(UploadChunkStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}