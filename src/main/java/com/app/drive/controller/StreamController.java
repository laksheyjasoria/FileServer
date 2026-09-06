package com.app.drive.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;

@RestController
@RequestMapping("/stream")
public class StreamController {

	private final MasterFileRepository repository;
	private final FileTransferService fileTransferService;
	private final HttpRangeParser httpRangeParser;

	public StreamController(MasterFileRepository repository, FileTransferService fileTransferService,
			HttpRangeParser httpRangeParser) {
		this.repository = repository;
		this.fileTransferService = fileTransferService;
		this.httpRangeParser = httpRangeParser;
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> stream(@PathVariable String id, org.springframework.security.core.Authentication auth,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

		MasterFile file = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName())
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);

		if (!"FILE".equalsIgnoreCase(file.getDriveType())) {
			throw new IllegalArgumentException("Only files can be streamed.");
		}

		if (file.getSize() == null || file.getSize() < 0) {
			throw new IllegalStateException("File size is missing or invalid.");
		}

		if (!fileTransferService.isChunked(file) && !fileTransferService.isLegacy(file)) {
			throw new IllegalArgumentException("File has no valid storage reference: " + file.getId());
		}

		MediaType contentType = resolveContentType(file.getContentType());

		/*
		 * Empty files cannot have a valid byte range. Return a normal empty response.
		 */
		if (file.getSize() == 0) {
			StreamingResponseBody body = outputStream -> {
				// Nothing to stream.
			};

			return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.ACCEPT_RANGES, "bytes")
					.header(HttpHeaders.CONTENT_DISPOSITION, buildInlineDisposition(file)).contentLength(0).body(body);
		}

		final ByteRange resolvedRange;

		try {
			if (rangeHeader == null || rangeHeader.isBlank()) {
				resolvedRange = new ByteRange(0L, file.getSize() - 1L);
			} else {
				RangeRequest rangeRequest = httpRangeParser.parse(rangeHeader);
				resolvedRange = httpRangeParser.resolve(rangeRequest, file.getSize());
			}
		} catch (IllegalArgumentException ex) {
			return buildRangeNotSatisfiableResponse(file.getSize());
		}

		StreamingResponseBody body = outputStream -> fileTransferService.streamRange(file, resolvedRange, outputStream);

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(contentType);
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
		headers.set(HttpHeaders.CONTENT_DISPOSITION, buildInlineDisposition(file));
		headers.setContentLength(resolvedRange.getLength());

		/*
		 * A Range request produces 206 Partial Content. A request without Range
		 * produces the complete file with 200.
		 */
		if (rangeHeader == null || rangeHeader.isBlank()) {
			return new ResponseEntity<>(body, headers, HttpStatus.OK);
		}

		headers.set(HttpHeaders.CONTENT_RANGE,
				"bytes " + resolvedRange.getStart() + "-" + resolvedRange.getEnd() + "/" + file.getSize());

		return new ResponseEntity<>(body, headers, HttpStatus.PARTIAL_CONTENT);
	}

	private ResponseEntity<Void> buildRangeNotSatisfiableResponse(long fileSize) {

		HttpHeaders headers = new HttpHeaders();

		headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);

		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

		return new ResponseEntity<>(headers, HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
	}

	private String buildInlineDisposition(MasterFile file) {
		ContentDisposition disposition = ContentDisposition.inline().filename(file.getName()).build();

		return disposition.toString();
	}

	private MediaType resolveContentType(String contentType) {

		if (contentType == null || contentType.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}

		try {
			return MediaType.parseMediaType(contentType);
		} catch (IllegalArgumentException ex) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}