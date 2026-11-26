package com.app.drive.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;

public class PreviewControllerTest {

	@Mock
	private MasterFileRepository repository;

	@Mock
	private FileTransferService fileTransferService;

	@Mock
	private Authentication authentication;

	private HttpRangeParser rangeParser;
	private PreviewController controller;

	@BeforeEach
	void setUp() {

		MockitoAnnotations.openMocks(this);

		rangeParser = new HttpRangeParser();

		controller = new PreviewController(repository, fileTransferService, rangeParser);
	}

	@Test
	void shouldReturnInlineFullPreview() {

		MasterFile file = createFile("test.jpg", 1000L, "image/jpeg");

		when(authentication.getName()).thenReturn("user1");

		when(repository.findByIdAndUserIdAndActiveTrue("file1", "user1")).thenReturn(Optional.of(file));

		ResponseEntity<?> response = controller.preview("file1", null, authentication);

		assertEquals(HttpStatus.OK, response.getStatusCode());

		assertEquals("inline; filename=\"test.jpg\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

		assertEquals("image/jpeg", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

		assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));

		assertEquals(1000L, response.getHeaders().getContentLength());
	}

	@Test
	void shouldReturnPartialPreviewForRange() {

		MasterFile file = createFile("video.mp4", 1000L, "video/mp4");

		when(authentication.getName()).thenReturn("user1");

		when(repository.findByIdAndUserIdAndActiveTrue("file1", "user1")).thenReturn(Optional.of(file));

		ResponseEntity<?> response = controller.preview("file1", "bytes=100-199", authentication);

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("bytes 100-199/1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(100L, response.getHeaders().getContentLength());

		assertEquals("video/mp4", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

		assertEquals("inline; filename=\"video.mp4\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
	}

	@Test
	void shouldSupportOpenEndedRange() {

		MasterFile file = createFile("video.mp4", 1000L, "video/mp4");

		when(authentication.getName()).thenReturn("user1");

		when(repository.findByIdAndUserIdAndActiveTrue("file1", "user1")).thenReturn(Optional.of(file));

		ResponseEntity<?> response = controller.preview("file1", "bytes=900-", authentication);

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("bytes 900-999/1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(100L, response.getHeaders().getContentLength());
	}

	@Test
	void shouldSupportSuffixRange() {

		MasterFile file = createFile("video.mp4", 1000L, "video/mp4");

		when(authentication.getName()).thenReturn("user1");

		when(repository.findByIdAndUserIdAndActiveTrue("file1", "user1")).thenReturn(Optional.of(file));

		ResponseEntity<?> response = controller.preview("file1", "bytes=-100", authentication);

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("bytes 900-999/1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(100L, response.getHeaders().getContentLength());
	}

	@Test
	void shouldReturn416ForInvalidRange() {

		MasterFile file = createFile("video.mp4", 1000L, "video/mp4");

		when(authentication.getName()).thenReturn("user1");

		when(repository.findByIdAndUserIdAndActiveTrue("file1", "user1")).thenReturn(Optional.of(file));

		ResponseEntity<?> response = controller.preview("file1", "bytes=1000-1200", authentication);

		assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());

		assertEquals("bytes */1000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
	}

	@Test
	void shouldFallbackToOctetStream() {

		MasterFile file = createFile("unknown.bin", 100L, null);

		when(authentication.getName()).thenReturn("user1");

		when(repository.findByIdAndUserIdAndActiveTrue("file1", "user1")).thenReturn(Optional.of(file));

		ResponseEntity<?> response = controller.preview("file1", null, authentication);

		assertEquals("application/octet-stream", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
	}

	private MasterFile createFile(String name, long size, String contentType) {

		MasterFile file = new MasterFile();

		file.setId("file1");
		file.setUserId("user1");
		file.setName(name);
		file.setSize(size);
		file.setContentType(contentType);
		file.setActive(true);

		/*
		 * Use a legacy file for controller header/range tests. Transfer behavior itself
		 * is tested by FileTransferService tests.
		 */
		file.setFileId("telegram-file-id");

		return file;
	}
}