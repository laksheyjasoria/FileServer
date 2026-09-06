package com.app.transfer;

public class RangeRequest {

	public enum Type {
		FULL, START_END, START_ONLY, SUFFIX
	}

	private final Type type;
	private final long start;
	private final long end;

	private RangeRequest(Type type, long start, long end) {

		this.type = type;
		this.start = start;
		this.end = end;
	}

	public static RangeRequest full() {
		return new RangeRequest(Type.FULL, 0L, -1L);
	}

	public static RangeRequest startEnd(long start, long end) {

		if (start < 0) {
			throw new IllegalArgumentException("Range start cannot be negative");
		}

		if (end < start) {
			throw new IllegalArgumentException("Range end cannot be smaller than start");
		}

		return new RangeRequest(Type.START_END, start, end);
	}

	public static RangeRequest startOnly(long start) {

		if (start < 0) {
			throw new IllegalArgumentException("Range start cannot be negative");
		}

		return new RangeRequest(Type.START_ONLY, start, -1L);
	}

	public static RangeRequest suffix(long length) {

		if (length <= 0) {
			throw new IllegalArgumentException("Suffix length must be positive");
		}

		return new RangeRequest(Type.SUFFIX, length, -1L);
	}

	public Type getType() {
		return type;
	}

	public long getStart() {
		return start;
	}

	public long getEnd() {
		return end;
	}
}