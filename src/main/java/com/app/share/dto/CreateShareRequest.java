package com.app.share.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.app.share.entity.SharePermission;

public class CreateShareRequest {

	private String fileId;

	private boolean publicAccess;

	private String password;

	private LocalDateTime expiry;

	private SharePermission permission;

	private List<String> allowedUsers;

	public String getFileId() {
		return fileId;
	}

	public void setFileId(String fileId) {
		this.fileId = fileId;
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

	public SharePermission getPermission() {
		return permission;
	}

	public void setPermission(SharePermission permission) {
		this.permission = permission;
	}

	public List<String> getAllowedUsers() {
		return allowedUsers;
	}

	public void setAllowedUsers(List<String> allowedUsers) {
		this.allowedUsers = allowedUsers;
	}
}