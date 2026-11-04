package com.app.upload.dto;

public class CreateUploadRequest {

    private String fileName;

    private Long totalSize;

    private Integer totalChunks;

    private String parentId;

    private String contentType;

    private String fileFingerprint;

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

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFileFingerprint() {
        return fileFingerprint;
    }

    public void setFileFingerprint(String fileFingerprint) {
        this.fileFingerprint = fileFingerprint;
    }
}