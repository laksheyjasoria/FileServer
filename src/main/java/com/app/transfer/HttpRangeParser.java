package com.app.transfer;

import org.springframework.stereotype.Component;

@Component
public class HttpRangeParser {

	public RangeRequest parse(String header) {

		if (header == null || header.isBlank()) {
			throw new IllegalArgumentException("Range header is empty");
		}

		String value = header.trim();

		int equalsIndex = value.indexOf('=');

		if (equalsIndex <= 0) {
			throw new IllegalArgumentException("Invalid Range header");
		}

		String unit = value.substring(0, equalsIndex).trim();

		if (!"bytes".equalsIgnoreCase(unit)) {
			throw new IllegalArgumentException("Only bytes ranges are supported");
		}

		String rangeValue = value.substring(equalsIndex + 1).trim();

		if (rangeValue.isEmpty()) {
			throw new IllegalArgumentException("Range value is empty");
		}

		/*
		 * Multiple ranges are intentionally not supported.
		 */
		if (rangeValue.contains(",")) {
			throw new IllegalArgumentException("Multiple ranges are not supported");
		}

		if (rangeValue.startsWith("-")) {

			String suffix = rangeValue.substring(1).trim();

			if (suffix.isEmpty()) {
				throw new IllegalArgumentException("Invalid suffix range");
			}

			long suffixLength = parsePositiveLong(suffix);

			return RangeRequest.suffix(suffixLength);
		}

		int dashIndex = rangeValue.indexOf('-');

		if (dashIndex < 0) {
			throw new IllegalArgumentException("Invalid byte range");
		}

		String startText = rangeValue.substring(0, dashIndex).trim();
		String endText = rangeValue.substring(dashIndex + 1).trim();

		if (startText.isEmpty()) {
			throw new IllegalArgumentException("Invalid byte range");
		}

		long start = parseNonNegativeLong(startText);

		/*
		 * bytes=start-
		 */
		if (endText.isEmpty()) {
			return RangeRequest.startOnly(start);
		}

		long end = parseNonNegativeLong(endText);

		if (end < start) {
			throw new IllegalArgumentException("Range end is smaller than range start");
		}

		return RangeRequest.startEnd(start, end);
	}

	public ByteRange resolve(RangeRequest request, long fileSize) {

		if (request == null) {
			throw new IllegalArgumentException("Range request is null");
		}

		if (fileSize < 0) {
			throw new IllegalArgumentException("File size cannot be negative");
		}

		if (fileSize == 0) {
			throw new IllegalArgumentException("Cannot resolve range for empty file");
		}

		switch (request.getType()) {

		case FULL:
			return new ByteRange(0L, fileSize - 1L);

		case START_END: {

			long start = request.getStart();
			long requestedEnd = request.getEnd();

			if (start < 0 || start >= fileSize) {
				throw new IllegalArgumentException("Range start is outside file");
			}

			long end = Math.min(requestedEnd, fileSize - 1L);

			if (end < start) {
				throw new IllegalArgumentException("Invalid resolved range");
			}

			return new ByteRange(start, end);
		}

		case START_ONLY: {

			long start = request.getStart();

			if (start < 0 || start >= fileSize) {
				throw new IllegalArgumentException("Range start is outside file");
			}

			return new ByteRange(start, fileSize - 1L);
		}

		case SUFFIX: {

			long suffixLength = request.getStart();

			if (suffixLength <= 0) {
				throw new IllegalArgumentException("Suffix length must be positive");
			}

			long actualLength = Math.min(suffixLength, fileSize);

			long start = fileSize - actualLength;

			return new ByteRange(start, fileSize - 1L);
		}

		default:
			throw new IllegalArgumentException("Unsupported range type");
		}
	}

	private long parsePositiveLong(String value) {

		try {
			long result = Long.parseLong(value);

			if (result <= 0) {
				throw new IllegalArgumentException("Value must be positive");
			}

			return result;

		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid numeric range value", ex);
		}
	}

	private long parseNonNegativeLong(String value) {

		try {
			long result = Long.parseLong(value);

			if (result < 0) {
				throw new IllegalArgumentException("Value cannot be negative");
			}

			return result;

		} catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid numeric range value", ex);
		}
	}
}