package com.app.core.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
	USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "User already exists"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
	INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid or expired token"),
	ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"), VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong"),
	OAUTH_ERROR(HttpStatus.UNAUTHORIZED, "OAuth authentication failed"),
	FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "File size limit exceeded"),
	EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email"),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized"), LOGGER_NOT_FOUND(HttpStatus.NOT_FOUND, "Logger not found"),
	BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"), FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "File not found"),
	UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "Upload job not found"),
	INVALID_SHARE(HttpStatus.UNAUTHORIZED, "Invalid share"),
	SHARE_PASSWORD_REQUIRED(HttpStatus.UNAUTHORIZED, "Share password required"),
	INVALID_SHARE_PASSWORD(HttpStatus.UNAUTHORIZED, "Invalid share password"),
	PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Plan not found"),
	SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Subscription not found"),
	WEBHOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "Webhook not found"),
	STORAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Storage limit exceeded"),
	UPLOAD_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "Upload limit exceeded"),
	SHARE_AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED,
			"This share is restricted to registered users. Please log in."),
	SHARE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not authorized to access this share.");

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