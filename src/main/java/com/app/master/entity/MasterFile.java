package com.app.master.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "master_files")
public class MasterFile {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	private String userId;

	private String name;

	private String fileId;

	/**
	 * Identifies a file whose physical storage is composed of
	 * Telegram chunks managed by an UploadJob.
	 *
	 * Legacy files continue to use fileId with uploadJobId == null.
	 */
	@Column(name = "upload_job_id")
	private String uploadJobId;

	private Long size;

	private String contentType;

	// Parent folder id (null for root)
	private String parentId;

	// Type: FILE or FOLDER
	private String driveType;

	// Access type: PUBLIC or PROTECTED
	private String accessType;

	private LocalDateTime createdAt;

	@Transient
	private int childrenCount;

	@Column(nullable = false)
	private boolean active = true;

	private LocalDateTime deletedAt;

	@PrePersist
	public void prePersist() {
		createdAt = LocalDateTime.now();
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
	}

	public String getUploadJobId() {
		return uploadJobId;
	}

	public void setUploadJobId(String uploadJobId) {
		this.uploadJobId = uploadJobId;
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getParentId() {
		return parentId;
	}

	public void setParentId(String parentId) {
		this.parentId = parentId;
	}

	public String getDriveType() {
		return driveType;
	}

	public void setDriveType(String driveType) {
		this.driveType = driveType;
	}

	public String getAccessType() {
		return accessType;
	}

	public void setAccessType(String accessType) {
		this.accessType = accessType;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public int getChildrenCount() {
		return childrenCount;
	}

	public void setChildrenCount(int childrenCount) {
		this.childrenCount = childrenCount;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(LocalDateTime deletedAt) {
		this.deletedAt = deletedAt;
	}
}