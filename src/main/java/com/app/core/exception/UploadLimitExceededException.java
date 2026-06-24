package com.app.core.exception;

public class UploadLimitExceededException extends AppException {

	public UploadLimitExceededException() {
		super(ErrorCode.UPLOAD_LIMIT_EXCEEDED, "Upload limit exceeded");
	}
}