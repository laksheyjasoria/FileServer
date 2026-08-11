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
	private List<String> allowedUsers;

	public CreateMultiShareRequest() {
	}

	public CreateMultiShareRequest(List<String> fileIds, boolean publicAccess, String password, LocalDateTime expiry,
			SharePermission permission, List<String> allowedUsers) {
		this.fileIds = fileIds;
		this.publicAccess = publicAccess;
		this.password = password;
		this.expiry = expiry;
		this.permission = permission;
		this.allowedUsers = allowedUsers;
	}

	public SharePermission getPermission() {
		return permission;
	}

	public void setPermission(SharePermission permission) {
		this.permission = permission;
	}

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

	public List<String> getAllowedUsers() {
		return allowedUsers;
	}

	public void setAllowedUsers(List<String> allowedUsers) {
		this.allowedUsers = allowedUsers;
	}
}