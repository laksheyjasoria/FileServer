package com.app.share.dto;

import java.time.LocalDateTime;

public record PublicShareResponse(String token, String driveName, String driveType, String shareType,
		LocalDateTime expiresAt, LocalDateTime createdAt) {
}