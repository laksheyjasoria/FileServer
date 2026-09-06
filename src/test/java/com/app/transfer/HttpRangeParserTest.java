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

		HttpRange range = parser.parse("bytes=100-199", 1000);

		assertEquals(100, range.getStart());

		assertEquals(199, range.getEnd());

		assertEquals(100, range.getLength());
	}

	@Test
	void shouldParseOpenEndedRange() {

		HttpRange range = parser.parse("bytes=500-", 1000);

		assertEquals(500, range.getStart());

		assertEquals(999, range.getEnd());

		assertEquals(500, range.getLength());
	}

	@Test
	void shouldParseSuffixRange() {

		HttpRange range = parser.parse("bytes=-200", 1000);

		assertEquals(800, range.getStart());

		assertEquals(999, range.getEnd());

		assertEquals(200, range.getLength());
	}

	@Test
	void shouldClampEndToFileSize() {

		HttpRange range = parser.parse("bytes=900-5000", 1000);

		assertEquals(900, range.getStart());

		assertEquals(999, range.getEnd());
	}

	@Test
	void shouldClampSuffixLargerThanFile() {

		HttpRange range = parser.parse("bytes=-5000", 1000);

		assertEquals(0, range.getStart());

		assertEquals(999, range.getEnd());

		assertEquals(1000, range.getLength());
	}

	@Test
	void shouldRejectMultipleRanges() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes=0-99,200-299", 1000));
	}

	@Test
	void shouldRejectInvalidUnit() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("items=0-99", 1000));
	}

	@Test
	void shouldRejectRangeStartingAfterFile() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes=1000-", 1000));
	}

	@Test
	void shouldRejectEndBeforeStart() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes=500-400", 1000));
	}

	@Test
	void shouldRejectEmptyRange() {

		assertThrows(IllegalArgumentException.class, () -> parser.parse("bytes=", 1000));
	}

	@Test
	void shouldAcceptUpperCaseBytesUnit() {

		HttpRange range = parser.parse("BYTES=10-20", 100);

		assertEquals(10, range.getStart());

		assertEquals(20, range.getEnd());
	}
}