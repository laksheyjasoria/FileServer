package com.app.share.dto;

import java.time.LocalDateTime;

import com.app.share.entity.SharePermission;

public record PublicShareResponse(String token, String driveName, String driveType, String shareType,
		LocalDateTime expiresAt, LocalDateTime createdAt, SharePermission permission, String createdBy, Long fileSize) {
}