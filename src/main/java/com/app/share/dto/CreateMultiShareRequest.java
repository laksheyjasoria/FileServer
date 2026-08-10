package com.app.share.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.app.share.entity.SharePermission;

public class CreateMultiShareRequest {

	private List<String> fileIds;
	private boolean publicAccess;
	private String password;
	private LocalDateTime expiry;
	private SharePermission permission;

	public SharePermission getPermission() {
		return permission;
	}

	public void setPermission(SharePermission permission) {
		this.permission = permission;
	}

	// Default no-args constructor
	public CreateMultiShareRequest() {
	}

	// All-args constructor
	public CreateMultiShareRequest(List<String> fileIds, boolean publicAccess, String password, LocalDateTime expiry,SharePermission permission) {
		this.fileIds = fileIds;
		this.publicAccess = publicAccess;
		this.password = password;
		this.expiry = expiry;
		this.permission = permission;
	}

	// Getters and Setters

	public List<String> getFileIds() {
		return fileIds;
	}

	public void setFileIds(List<String> fileIds) {
		this.fileIds = fileIds;
	}

	public boolean isPublicAccess() {
		return publicAccess;
	}

	public void setPublicAccess(boolean publicAccess) {
		this.publicAccess = publicAccess;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public LocalDateTime getExpiry() {
		return expiry;
	}

	public void setExpiry(LocalDateTime expiry) {
		this.expiry = expiry;
	}
}