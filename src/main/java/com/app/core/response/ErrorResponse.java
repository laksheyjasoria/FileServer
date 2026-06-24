package com.app.core.response;

public class ErrorResponse {

	private boolean success;
	private int status;
	private String error;
	private String message;

	public ErrorResponse(int status, String error, String message) {
		this.success = false;
		this.status = status;
		this.error = error;
		this.message = message;
	}

	public boolean isSuccess() {
		return success;
	}

	public int getStatus() {
		return status;
	}

	public String getError() {
		return error;
	}

	public String getMessage() {
		return message;
	}
}