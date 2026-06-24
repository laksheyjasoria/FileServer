package com.app.share.dto;

import java.time.LocalDateTime;

public class CreateShareRequest {

	private String fileId;

	private boolean publicAccess;

	private String password;

	private LocalDateTime expiry;

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