package com.app.share.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "shared_resources")
public class SharedResource {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	private String token;

	private String fileId; // single file (optional, for backward compatibility)

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "shared_resource_files", joinColumns = @JoinColumn(name = "share_id"))
	@Column(name = "file_id")
	private List<String> fileIds = new ArrayList<>(); // multiple files/folders

	private String createdBy;

	private boolean publicAccess;

	private String password;

	private LocalDateTime expiry;

	private LocalDateTime createdAt;

	@Enumerated(EnumType.STRING)
	private SharePermission permission = SharePermission.VIEW_DOWNLOAD; // default

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

	public List<String> getFileIds() {
		return fileIds;
	}

	public void setFileIds(List<String> fileIds) {
		this.fileIds = fileIds;
	}

	public SharePermission getPermission() {
		return permission;
	}

	public void setPermission(SharePermission permission) {
		this.permission = permission;
	}
}