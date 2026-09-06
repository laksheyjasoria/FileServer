package com.app.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpRangeParserTest {

	private HttpRangeParser parser;

	@BeforeEach
	void setUp() {
		parser = new HttpRangeParser();
	}

	@Test
	void shouldParseStartEndRange() {

		RangeRequest request = parser.parse("bytes=100-199");

		ByteRange range = parser.resolve(request, 1000);

		assertEquals(100, range.getStart());
		assertEquals(199, range.getEnd());
		assertEquals(100, range.getLength());
	}

	@Test
	void shouldParseOpenEndedRange() {

		RangeRequest request = parser.parse("bytes=500-");

		ByteRange range = parser.resolve(request, 1000);

		assertEquals(500, range.getStart());
		assertEquals(999, range.getEnd());
		assertEquals(500, range.getLength());
	}

	@Test
	void shouldParseSuffixRange() {

		RangeRequest request = parser.parse("bytes=-200");

		ByteRange range = parser.resolve(request, 1000);

		assertEquals(800, range.getStart());
		assertEquals(999, range.getEnd());
		assertEquals(200, range.getLength());
	}

	@Test
	void shouldClampEndToFileSize() {

		RangeRequest request = parser.parse("bytes=900-5000");

		ByteRange range = parser.resolve(request, 1000);

		assertEquals(900, range.getStart());
		assertEquals(999, range.getEnd());
		assertEquals(100, range.getLength());
	}

	@Test
	void shouldClampSuffixLargerThanFile() {

		RangeRequest request = parser.parse("bytes=-5000");

		ByteRange range = parser.resolve(request, 1000);

		assertEquals(0, range.getStart());
		assertEquals(999, range.getEnd());
		assertEquals(1000, range.getLength());
	}

	@Test
	void shouldRejectMultipleRanges() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes=0-99,200-299"));
	}

	@Test
	void shouldRejectInvalidUnit() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("items=0-99"));
	}

	@Test
	void shouldRejectRangeStartingAtFileSize() {

		RangeRequest request = parser.parse("bytes=1000-");

		assertThrows(IllegalArgumentException.class, () -> parser.resolve(request, 1000));
	}

	@Test
	void shouldRejectRangeStartingAfterFile() {

		RangeRequest request = parser.parse("bytes=1500-1600");

		assertThrows(IllegalArgumentException.class, () -> parser.resolve(request, 1000));
	}

	@Test
	void shouldRejectEndBeforeStart() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes=500-400"));
	}

	@Test
	void shouldRejectEmptyRange() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes="));
	}

	@Test
	void shouldAcceptUpperCaseBytesUnit() {

		RangeRequest request = parser.parse("BYTES=10-20");

		ByteRange range = parser.resolve(request, 100);

		assertEquals(10, range.getStart());
		assertEquals(20, range.getEnd());
		assertEquals(11, range.getLength());
	}

	@Test
	void shouldResolveFullRange() {

		RangeRequest request = RangeRequest.full();

		ByteRange range = parser.resolve(request, 1000);

		assertEquals(0, range.getStart());
		assertEquals(999, range.getEnd());
		assertEquals(1000, range.getLength());
	}

	@Test
	void shouldRejectEmptyFile() {

		RangeRequest request = parser.parse("bytes=0-10");

		assertThrows(IllegalArgumentException.class, () -> parser.resolve(request, 0));
	}
}