package com.app.upload.dto;

public class ChunkUploadResponse {

    private String uploadId;
    private Integer uploadedChunks;
    private Integer totalChunks;
    private String status;

    public ChunkUploadResponse() {
    }

    public ChunkUploadResponse(String uploadId,
                               Integer uploadedChunks,
                               Integer totalChunks,
                               String status) {
        this.uploadId = uploadId;
        this.uploadedChunks = uploadedChunks;
        this.totalChunks = totalChunks;
        this.status = status;
    }

    public String getUploadId() {
        return uploadId;
    }

    public Integer getUploadedChunks() {
        return uploadedChunks;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public String getStatus() {
        return status;
    }
}