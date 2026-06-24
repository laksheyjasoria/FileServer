package com.app.upload.dto;

public class CreateUploadRequest {

    private String fileName;
    private Long totalSize;
    private Integer totalChunks;

    public String getFileName() {
        return fileName;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public Integer getTotalChunks() {
        return totalChunks;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public void setTotalChunks(Integer totalChunks) {
        this.totalChunks = totalChunks;
    }
}