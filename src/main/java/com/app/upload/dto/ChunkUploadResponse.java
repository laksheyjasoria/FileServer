package com.app.upload.dto;

public class ChunkUploadResponse {

	private String uploadId;

	private String fileName;

	private Long totalSize;

	private Integer chunkSize;

	private Integer uploadedChunks;

	private Integer totalChunks;

	private Long uploadedBytes;

	private Double progress;

	private String status;

	public ChunkUploadResponse() {
	}

	public ChunkUploadResponse(String uploadId, String fileName, Long totalSize, Integer chunkSize,
			Integer uploadedChunks, Integer totalChunks, Long uploadedBytes, Double progress, String status) {

		this.uploadId = uploadId;
		this.fileName = fileName;
		this.totalSize = totalSize;
		this.chunkSize = chunkSize;
		this.uploadedChunks = uploadedChunks;
		this.totalChunks = totalChunks;
		this.uploadedBytes = uploadedBytes;
		this.progress = progress;
		this.status = status;
	}

	public String getUploadId() {
		return uploadId;
	}

	public String getFileName() {
		return fileName;
	}

	public Long getTotalSize() {
		return totalSize;
	}

	public Integer getChunkSize() {
		return chunkSize;
	}

	public Integer getUploadedChunks() {
		return uploadedChunks;
	}

	public Integer getTotalChunks() {
		return totalChunks;
	}

	public Long getUploadedBytes() {
		return uploadedBytes;
	}

	public Double getProgress() {
		return progress;
	}

	public String getStatus() {
		return status;
	}
}