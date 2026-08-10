package com.app.share.dto;

import java.time.LocalDateTime;

import com.app.share.entity.SharePermission;

public class CreateShareRequest {

	private String fileId;

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

	public String getFileId() {
		return fileId;
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

	public void setFileId(String fileId) {
		this.fileId = fileId;
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
}