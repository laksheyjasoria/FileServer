package com.app.transfer;

import org.springframework.stereotype.Service;

@Service
public class HttpRangeParser {

	public HttpRange parse(String rangeHeader, long fileSize) {

		if (rangeHeader == null || rangeHeader.isBlank()) {

			throw new IllegalArgumentException("Range header cannot be empty.");
		}

		if (fileSize <= 0) {
			throw new IllegalArgumentException("Cannot create a range for an empty file.");
		}

		String value = rangeHeader.trim();

		if (!value.regionMatches(true, 0, "bytes=", 0, 6)) {

			throw new IllegalArgumentException("Only byte ranges are supported.");
		}

		String rangeValue = value.substring(6).trim();

		if (rangeValue.isEmpty()) {
			throw new IllegalArgumentException("Byte range cannot be empty.");
		}

		/*
		 * Phase 5 intentionally supports one range only.
		 *
		 * Multiple ranges require multipart/byteranges responses, which will be added
		 * only if the application needs them.
		 */
		if (rangeValue.contains(",")) {
			throw new IllegalArgumentException("Multiple byte ranges are not supported.");
		}

		int dashIndex = rangeValue.indexOf('-');

		if (dashIndex < 0) {
			throw new IllegalArgumentException("Invalid byte range.");
		}

		String startPart = rangeValue.substring(0, dashIndex).trim();

		String endPart = rangeValue.substring(dashIndex + 1).trim();

		if (startPart.isEmpty()) {

			return parseSuffix(endPart, fileSize);
		}

		long start = parseNonNegativeLong(startPart, "Range start");

		if (start >= fileSize) {
			throw new IllegalArgumentException("Range start is outside the file.");
		}

		if (endPart.isEmpty()) {

			return new HttpRange(start, fileSize - 1);
		}

		long requestedEnd = parseNonNegativeLong(endPart, "Range end");

		if (requestedEnd < start) {
			throw new IllegalArgumentException("Range end cannot be smaller than range start.");
		}

		long end = Math.min(requestedEnd, fileSize - 1);

		return new HttpRange(start, end);
	}

	private HttpRange parseSuffix(String suffixPart, long fileSize) {

		if (suffixPart.isEmpty()) {
			throw new IllegalArgumentException("Suffix range length cannot be empty.");
		}

		long suffixLength = parseNonNegativeLong(suffixPart, "Suffix range length");

		if (suffixLength <= 0) {
			throw new IllegalArgumentException("Suffix range length must be greater than zero.");
		}

		long actualLength = Math.min(suffixLength, fileSize);

		long start = fileSize - actualLength;

		return new HttpRange(start, fileSize - 1);
	}

	private long parseNonNegativeLong(String value, String fieldName) {

		try {

			long parsed = Long.parseLong(value);

			if (parsed < 0) {
				throw new IllegalArgumentException(fieldName + " cannot be negative.");
			}

			return parsed;

		} catch (NumberFormatException ex) {

			throw new IllegalArgumentException("Invalid " + fieldName.toLowerCase() + ".");
		}
	}
}