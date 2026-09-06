package com.app.transfer;

public class HttpRange {

	private final long start;
	private final long end;

	public HttpRange(long start, long end) {

		if (start < 0) {
			throw new IllegalArgumentException("Range start cannot be negative.");
		}

		if (end < start) {
			throw new IllegalArgumentException("Range end cannot be smaller than start.");
		}

		this.start = start;
		this.end = end;
	}

	public long getStart() {
		return start;
	}

	public long getEnd() {
		return end;
	}

	public long getLength() {
		return end - start + 1;
	}

	@Override
	public String toString() {
		return "HttpRange{" + "start=" + start + ", end=" + end + ", length=" + getLength() + '}';
	}
}