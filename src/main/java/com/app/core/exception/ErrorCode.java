package com.app.core.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	// Authentication & User
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
	USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "User already exists"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"), UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"),
	OAUTH_ERROR(HttpStatus.UNAUTHORIZED, "OAuth authentication failed"),
	ACCOUNT_DEACTIVATED(HttpStatus.FORBIDDEN, "Your account has been deactivated. Please contact the administrator."),
	INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Password does not meet security requirements"),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "New password must be different from the current password"),

	// Validation & Bad Requests
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"), BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
	FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "File size limit exceeded"),
	STORAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Storage limit exceeded"),
	UPLOAD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Upload limit exceeded"),

	// Not Found
	LOGGER_NOT_FOUND(HttpStatus.NOT_FOUND, "Logger not found"), FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "File not found"),
	UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "Upload job not found"),
	PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Plan not found"),
	SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Subscription not found"),
	WEBHOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "Webhook not found"),

	// Share (Public / Protected)
	SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "The share link is invalid."),
	SHARE_EXPIRED(HttpStatus.GONE, "This share link has expired."),
	SHARE_REMOVED(HttpStatus.GONE, "This share has been removed by the owner."),
	SHARE_FILE_REMOVED(HttpStatus.GONE, "One or more shared files have been removed by the owner."),
	SHARE_PASSWORD_REQUIRED(HttpStatus.UNAUTHORIZED, "Share password required"),
	INVALID_SHARE_PASSWORD(HttpStatus.UNAUTHORIZED, "Invalid share password"),

	// Share (User-Only)
	SHARE_AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED,
			"This share is restricted to registered users. Please log in."),
	SHARE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not authorized to access this share."),

	// Email
	EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email"),

	// Miscellaneous
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}