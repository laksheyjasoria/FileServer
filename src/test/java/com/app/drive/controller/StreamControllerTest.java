package com.app.drive.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;

@ExtendWith(MockitoExtension.class)
class StreamControllerTest {

	@Mock
	private MasterFileRepository repository;

	@Mock
	private FileTransferService fileTransferService;

	@Mock
	private HttpRangeParser httpRangeParser;

	@Mock
	private Authentication authentication;

	private StreamController controller;

	@BeforeEach
	void setUp() {
		controller = new StreamController(repository, fileTransferService, httpRangeParser);

		when(authentication.getName()).thenReturn("user-1");
	}

	@Test
	void shouldStreamCompleteChunkedFileWith200() throws Exception {

		MasterFile file = createFile("video.mp4", 5000L, "video/mp4");

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		ResponseEntity<?> response = controller.stream("file-1", authentication, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());

		assertEquals("video/mp4", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

		assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));

		assertEquals("inline; filename=\"video.mp4\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

		assertEquals(5000L, response.getHeaders().getContentLength());

		assertEquals(null, response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertNotNull(response.getBody());

		StreamingResponseBody body = (StreamingResponseBody) response.getBody();

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		doAnswer(invocation -> {

			ByteRange range = invocation.getArgument(1, ByteRange.class);

			assertEquals(0L, range.getStart());

			assertEquals(4999L, range.getEnd());

			return null;

		}).when(fileTransferService).streamRange(eq(file), any(ByteRange.class), any(OutputStream.class));

		body.writeTo(output);

		verify(fileTransferService).streamRange(eq(file), any(ByteRange.class), any(OutputStream.class));
	}

	@Test
	void shouldStreamRequestedRangeWith206() throws Exception {

		MasterFile file = createFile("video.mp4", 10000L, "video/mp4");

		RangeRequest request = RangeRequest.startEnd(1000L, 1999L);

		ByteRange resolvedRange = new ByteRange(1000L, 1999L);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		when(httpRangeParser.parse("bytes=1000-1999")).thenReturn(request);

		when(httpRangeParser.resolve(request, 10000L)).thenReturn(resolvedRange);

		ResponseEntity<?> response = controller.stream("file-1", authentication, "bytes=1000-1999");

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("video/mp4", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));

		assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));

		assertEquals("bytes 1000-1999/10000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(1000L, response.getHeaders().getContentLength());

		assertEquals("inline; filename=\"video.mp4\"", response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));

		StreamingResponseBody body = (StreamingResponseBody) response.getBody();

		assertNotNull(body);

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		body.writeTo(output);

		verify(fileTransferService).streamRange(eq(file), eq(resolvedRange), any(OutputStream.class));
	}

	@Test
	void shouldStreamOpenEndedRangeWith206() throws Exception {

		MasterFile file = createFile("video.mp4", 10000L, "video/mp4");

		RangeRequest request = RangeRequest.startOnly(5000L);

		ByteRange resolvedRange = new ByteRange(5000L, 9999L);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		when(httpRangeParser.parse("bytes=5000-")).thenReturn(request);

		when(httpRangeParser.resolve(request, 10000L)).thenReturn(resolvedRange);

		ResponseEntity<?> response = controller.stream("file-1", authentication, "bytes=5000-");

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("bytes 5000-9999/10000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(5000L, response.getHeaders().getContentLength());
	}

	@Test
	void shouldStreamSuffixRangeWith206() throws Exception {

		MasterFile file = createFile("video.mp4", 10000L, "video/mp4");

		RangeRequest request = RangeRequest.suffix(1000L);

		ByteRange resolvedRange = new ByteRange(9000L, 9999L);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		when(httpRangeParser.parse("bytes=-1000")).thenReturn(request);

		when(httpRangeParser.resolve(request, 10000L)).thenReturn(resolvedRange);

		ResponseEntity<?> response = controller.stream("file-1", authentication, "bytes=-1000");

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("bytes 9000-9999/10000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(1000L, response.getHeaders().getContentLength());
	}

	@Test
	void shouldReturn416ForInvalidRange() {

		MasterFile file = createFile("video.mp4", 10000L, "video/mp4");

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		when(httpRangeParser.parse("bytes=10000-11000"))
				.thenThrow(new IllegalArgumentException("Range start is outside the file"));

		ResponseEntity<?> response = controller.stream("file-1", authentication, "bytes=10000-11000");

		assertEquals(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, response.getStatusCode());

		assertEquals("bytes */10000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
	}

	@Test
	void shouldSupportAudioContentType() {

		MasterFile file = createFile("song.mp3", 8000L, "audio/mpeg");

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		ResponseEntity<?> response = controller.stream("file-1", authentication, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());

		assertEquals("audio/mpeg", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
	}

	@Test
	void shouldUseOctetStreamForMissingContentType() {

		MasterFile file = createFile("unknown.bin", 1000L, null);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		ResponseEntity<?> response = controller.stream("file-1", authentication, null);

		assertEquals("application/octet-stream", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
	}

	@Test
	void shouldStreamLegacyFile() throws Exception {

		MasterFile file = createFile("legacy.mp4", 5000L, "video/mp4");

		file.setFileId("telegram-file-id");
		file.setUploadJobId(null);

		RangeRequest request = RangeRequest.startEnd(100L, 199L);

		ByteRange resolvedRange = new ByteRange(100L, 199L);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(false);

		when(fileTransferService.isLegacy(file)).thenReturn(true);

		when(httpRangeParser.parse("bytes=100-199")).thenReturn(request);

		when(httpRangeParser.resolve(request, 5000L)).thenReturn(resolvedRange);

		ResponseEntity<?> response = controller.stream("file-1", authentication, "bytes=100-199");

		assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());

		assertEquals("bytes 100-199/5000", response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE));

		assertEquals(100L, response.getHeaders().getContentLength());

		StreamingResponseBody body = (StreamingResponseBody) response.getBody();

		assertNotNull(body);

		ByteArrayOutputStream output = new ByteArrayOutputStream();

		doAnswer(invocation -> {

			ByteRange range = invocation.getArgument(1, ByteRange.class);

			assertEquals(100L, range.getStart());

			assertEquals(199L, range.getEnd());

			return null;

		}).when(fileTransferService).streamRange(eq(file), any(ByteRange.class), any(OutputStream.class));

		body.writeTo(output);

		verify(fileTransferService).streamRange(eq(file), eq(resolvedRange), any(OutputStream.class));
	}

	@Test
	void shouldReturnEmptyResponseForEmptyFile() throws Exception {
		MasterFile file = new MasterFile();
		file.setId("file-1");
		file.setUserId("user-1");
		file.setName("empty.txt");
		file.setSize(0L);
		file.setContentType("text/plain");
		file.setDriveType("FILE");
		file.setFileId("telegram-file-id");
		file.setActive(true);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(false);
		when(fileTransferService.isLegacy(file)).thenReturn(true);

		ResponseEntity<?> response = controller.stream("file-1", authentication, null);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
		assertEquals("bytes", response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES));
		assertEquals(0L, response.getHeaders().getContentLength());
		assertNotNull(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
		assertNotNull(response.getBody());
	}

	@Test
	void shouldRejectNonFileDriveType() {

		MasterFile file = createFile("folder", 0L, null);

		file.setDriveType("FOLDER");

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		assertThrows(IllegalArgumentException.class, () -> controller.stream("file-1", authentication, null));
	}

	@Test
	void shouldRejectFileWithoutStorageReference() {

		MasterFile file = createFile("broken.mp4", 1000L, "video/mp4");

		file.setFileId(null);
		file.setUploadJobId(null);

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(false);

		when(fileTransferService.isLegacy(file)).thenReturn(false);

		assertThrows(IllegalArgumentException.class, () -> controller.stream("file-1", authentication, null));
	}

	@Test
	void shouldUseActiveOwnerScopedRepositoryLookup() {

		MasterFile file = createFile("video.mp4", 1000L, "video/mp4");

		when(repository.findByIdAndUserIdAndActiveTrue("file-1", "user-1")).thenReturn(Optional.of(file));

		when(fileTransferService.isChunked(file)).thenReturn(true);

		controller.stream("file-1", authentication, null);

		verify(repository).findByIdAndUserIdAndActiveTrue("file-1", "user-1");
	}

	private MasterFile createFile(String name, Long size, String contentType) {

		MasterFile file = new MasterFile();

		file.setId("file-1");
		file.setUserId("user-1");
		file.setName(name);
		file.setSize(size);
		file.setContentType(contentType);
		file.setDriveType("FILE");
		file.setActive(true);

		return file;
	}
}
