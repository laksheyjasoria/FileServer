package com.app.transfer;

public class RangeRequest {

	public enum Type {
		FULL, START_END, START_ONLY, SUFFIX
	}

	private final Type type;
	private final long start;
	private final long end;
	private final long suffixLength;

	private RangeRequest(Type type, long start, long end, long suffixLength) {

		this.type = type;
		this.start = start;
		this.end = end;
		this.suffixLength = suffixLength;
	}

	public static RangeRequest full() {
		return new RangeRequest(Type.FULL, 0, 0, 0);
	}

	public static RangeRequest startEnd(long start, long end) {

		if (start < 0) {
			throw new IllegalArgumentException("Range start cannot be negative.");
		}

		if (end < start) {
			throw new IllegalArgumentException("Range end cannot be smaller than start.");
		}

		return new RangeRequest(Type.START_END, start, end, 0);
	}

	public static RangeRequest startOnly(long start) {

		if (start < 0) {
			throw new IllegalArgumentException("Range start cannot be negative.");
		}

		return new RangeRequest(Type.START_ONLY, start, 0, 0);
	}

	public static RangeRequest suffix(long length) {

		if (length <= 0) {
			throw new IllegalArgumentException("Suffix range length must be greater than zero.");
		}

		return new RangeRequest(Type.SUFFIX, 0, 0, length);
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

	public long getSuffixLength() {
		return suffixLength;
	}

	public boolean isFull() {
		return type == Type.FULL;
	}

	@Override
	public String toString() {
		return "RangeRequest{" + "type=" + type + ", start=" + start + ", end=" + end + ", suffixLength=" + suffixLength
				+ '}';
	}
}