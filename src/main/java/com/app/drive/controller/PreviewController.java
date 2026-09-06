package com.app.drive.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;

@RestController
@RequestMapping("/preview")
public class PreviewController {

	private final MasterFileRepository masterFileRepository;
	private final FileTransferService fileTransferService;
	private final HttpRangeParser rangeParser;

	public PreviewController(MasterFileRepository masterFileRepository, FileTransferService fileTransferService,
			HttpRangeParser rangeParser) {

		this.masterFileRepository = masterFileRepository;
		this.fileTransferService = fileTransferService;
		this.rangeParser = rangeParser;
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> preview(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
			Authentication authentication) {

		MasterFile file = masterFileRepository.findByIdAndUserIdAndActiveTrue(id, authentication.getName())
				.orElseThrow(FileNotFoundException::new);

		if (file.getUploadJobId() == null && (file.getFileId() == null || file.getFileId().isBlank())) {
			throw new FileNotFoundException();
		}

		long fileSize = file.getSize() == null ? 0L : file.getSize();

		MediaType contentType = resolveContentType(file);

		/*
		 * Empty file.
		 */
		if (fileSize == 0L) {
			HttpHeaders headers = buildInlineHeaders(file, contentType);
			headers.setContentLength(0L);
			headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

			return ResponseEntity.ok().headers(headers).body(new byte[0]);
		}

		/*
		 * Parse HTTP Range.
		 */
		ByteRange range;

		try {
			if (rangeHeader == null || rangeHeader.isBlank()) {
				range = new ByteRange(0L, fileSize - 1L);
			} else {
				RangeRequest rangeRequest = rangeParser.parse(rangeHeader);
				range = rangeParser.resolve(rangeRequest, fileSize);
			}
		} catch (IllegalArgumentException ex) {
			HttpHeaders headers = new HttpHeaders();
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
			headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

			return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
		}

		boolean partial = rangeHeader != null && !rangeHeader.isBlank();

		HttpHeaders headers = buildInlineHeaders(file, contentType);
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
		headers.setContentLength(range.getLength());

		if (partial) {
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.getStart() + "-" + range.getEnd() + "/" + fileSize);
		}

		StreamingResponseBody body = outputStream -> {
			fileTransferService.streamRange(file, range, outputStream);
		};

		if (partial) {
			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
		}

		return ResponseEntity.ok().headers(headers).body(body);
	}

	private HttpHeaders buildInlineHeaders(MasterFile file, MediaType contentType) {

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(contentType);

		headers.setContentDisposition(ContentDisposition.inline().filename(file.getName()).build());

		return headers;
	}

	private MediaType resolveContentType(MasterFile file) {

		if (file.getContentType() == null || file.getContentType().isBlank()) {

			return MediaType.APPLICATION_OCTET_STREAM;
		}

		try {
			return MediaType.parseMediaType(file.getContentType());
		} catch (IllegalArgumentException ex) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}
}