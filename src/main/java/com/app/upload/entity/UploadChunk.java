package com.app.upload.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "upload_chunks")
public class UploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String uploadJobId;

    private Integer chunkIndex;

    private String telegramFileId;

    private Long size;

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
}