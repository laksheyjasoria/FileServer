package com.app.file.controller;

import java.util.Date;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.core.security.jwt.JwtService;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.storage.factory.StorageFactory;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/files")
public class FileController {

	private static final Logger log = LoggerFactory.getLogger(FileController.class);

	private final JwtService jwtService;
	private final StorageFactory storageFactory;
	private final MasterFileRepository masterFileRepository;
	private final FileTransferService fileTransferService;
	private final HttpRangeParser rangeParser;

	@Value("${app.file.token.validity-seconds:2592000}")
	private long defaultTokenValidity;

	public FileController(JwtService jwtService, StorageFactory storageFactory,
			MasterFileRepository masterFileRepository, FileTransferService fileTransferService,
			HttpRangeParser rangeParser) {

		this.jwtService = jwtService;
		this.storageFactory = storageFactory;
		this.masterFileRepository = masterFileRepository;
		this.fileTransferService = fileTransferService;
		this.rangeParser = rangeParser;
	}

	/**
	 * Existing storage-file signed URL used by profile/avatar functionality. This
	 * endpoint deliberately continues to work with the Telegram file ID stored
	 * directly in User.photoUrl.
	 */
	@GetMapping("/{fileId}/signed")
	public ResponseEntity<Map<String, String>> getSignedUrl(@PathVariable String fileId,
			@RequestParam(required = false) Long validity,
			@RequestParam(required = false, defaultValue = "false") boolean infinite, Authentication auth,
			HttpServletRequest request) {

		if (auth == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String token;

		if (infinite) {
			token = jwtService.generateFileAccessTokenInfinite(fileId);
		} else if (validity != null && validity > 0) {
			token = jwtService.generateFileAccessToken(fileId, validity);
		} else {
			Date sessionExpiry = getSessionExpiry(request);

			if (sessionExpiry != null) {
				token = jwtService.generateFileAccessToken(fileId, sessionExpiry);
			} else {
				token = jwtService.generateFileAccessToken(fileId);
			}
		}

		String signedUrl = "/api/files/stream/" + fileId + "?token=" + token;
		return ResponseEntity.ok(Map.of("url", signedUrl));
	}

	/**
	 * Existing avatar/storage-file signed URL shorthand.
	 */
	@GetMapping("/{fileId}/signed/session")
	public ResponseEntity<Map<String, String>> getSignedUrlSession(@PathVariable String fileId, Authentication auth,
			HttpServletRequest request) {

		if (auth == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		Date sessionExpiry = getSessionExpiry(request);

		if (sessionExpiry == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String token = jwtService.generateFileAccessToken(fileId, sessionExpiry);
		String signedUrl = "/api/files/stream/" + fileId + "?token=" + token;

		return ResponseEntity.ok(Map.of("url", signedUrl));
	}

	/**
	 * Generates a signed URL for a FileServer MasterFile.
	 *
	 * This is separate from the legacy storage-file signed URL above because
	 * User.photoUrl stores a Telegram file ID, while MasterFile.id is the
	 * application-level file identifier.
	 */
	@GetMapping("/drive/{fileId}/signed/session")
	public ResponseEntity<Map<String, String>> getDriveSignedUrlSession(@PathVariable String fileId,
			Authentication auth, HttpServletRequest request) {

		if (auth == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		requireOwnedActiveFile(fileId, auth.getName());

		Date sessionExpiry = getSessionExpiry(request);

		if (sessionExpiry == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		String token = jwtService.generateFileAccessToken(fileId, sessionExpiry);

		String signedUrl = "/api/files/drive/stream/" + fileId + "?token=" + token;

		return ResponseEntity.ok(Map.of("url", signedUrl));
	}

	@GetMapping("/drive/{fileId}/signed")
	public ResponseEntity<Map<String, String>> getDriveSignedUrl(@PathVariable String fileId,
			@RequestParam(required = false) Long validity,
			@RequestParam(required = false, defaultValue = "false") boolean infinite, Authentication auth) {

		if (auth == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		requireOwnedActiveFile(fileId, auth.getName());

		String token;

		if (infinite) {
			token = jwtService.generateFileAccessTokenInfinite(fileId);
		} else if (validity != null && validity > 0) {
			token = jwtService.generateFileAccessToken(fileId, validity);
		} else {
			token = jwtService.generateFileAccessToken(fileId, defaultTokenValidity);
		}

		String signedUrl = "/api/files/drive/stream/" + fileId + "?token=" + token;

		return ResponseEntity.ok(Map.of("url", signedUrl));
	}

	/**
	 * Legacy Telegram-storage stream used by profile/avatar URLs. Kept
	 * intentionally unchanged in terms of storage semantics.
	 */
	@GetMapping("/stream/{fileId}")
	public ResponseEntity<byte[]> streamStorageFile(@PathVariable String fileId, @RequestParam String token,
			@RequestParam(required = false, defaultValue = "false") boolean download) {

		String extractedFileId;

		try {
			extractedFileId = jwtService.validateFileAccessToken(token);
		} catch (Exception ex) {
			log.warn("Invalid storage file token.");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		if (!extractedFileId.equals(fileId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		byte[] content;

		try {
			content = storageFactory.get().download(fileId);
		} catch (Exception ex) {
			log.error("Storage file download error.", ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
		}

		if (content == null || content.length == 0) {
			return ResponseEntity.notFound().build();
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(detectMediaType(fileId));
		headers.setCacheControl("private, max-age=3600");

		if (download) {
			headers.setContentDisposition(ContentDisposition.attachment().filename(extractFilename(fileId)).build());
		} else {
			headers.setContentDisposition(ContentDisposition.builder("inline").build());
		}

		return ResponseEntity.ok().headers(headers).body(content);
	}

	/**
	 * Signed, Range-capable stream for FileServer files.
	 *
	 * The browser can use this URL directly in <video>, <audio>, <img> or PDF
	 * viewers, so Range requests are preserved instead of forcing the browser
	 * through a blob download.
	 */
	@GetMapping("/drive/stream/{fileId}")
	public ResponseEntity<?> streamDriveFile(@PathVariable String fileId, @RequestParam String token,
			@RequestParam(required = false, defaultValue = "false") boolean download,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

		String extractedFileId;

		try {
			extractedFileId = jwtService.validateFileAccessToken(token);
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		if (!fileId.equals(extractedFileId)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		MasterFile file = masterFileRepository.findByIdAndActiveTrue(fileId)
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);

		if (!"FILE".equalsIgnoreCase(file.getDriveType())) {
			throw new IllegalArgumentException("Only files can be streamed.");
		}

		if (!fileTransferService.isChunked(file) && !fileTransferService.isLegacy(file)) {
			throw new IllegalArgumentException("File has no valid storage reference: " + file.getId());
		}

		long fileSize = file.getSize() == null ? 0L : file.getSize();
		MediaType contentType = resolveContentType(file.getContentType());

		if (fileSize == 0L) {
			HttpHeaders headers = buildDriveHeaders(file, contentType, download);
			headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
			headers.setContentLength(0L);

			StreamingResponseBody body = outputStream -> {
				// Empty file.
			};

			return ResponseEntity.ok().headers(headers).body(body);
		}

		final ByteRange range;

		try {
			if (rangeHeader == null || rangeHeader.isBlank()) {
				range = new ByteRange(0L, fileSize - 1L);
			} else {
				RangeRequest request = rangeParser.parse(rangeHeader);
				range = rangeParser.resolve(request, fileSize);
			}
		} catch (IllegalArgumentException ex) {
			HttpHeaders headers = new HttpHeaders();
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
			headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

			return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
		}

		StreamingResponseBody body = outputStream -> fileTransferService.streamRange(file, range, outputStream);

		HttpHeaders headers = buildDriveHeaders(file, contentType, download);
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
		headers.setContentLength(range.getLength());

		boolean partial = rangeHeader != null && !rangeHeader.isBlank();

		if (partial) {
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + range.getStart() + "-" + range.getEnd() + "/" + fileSize);

			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
		}

		return ResponseEntity.ok().headers(headers).body(body);
	}

	private MasterFile requireOwnedActiveFile(String fileId, String userId) {
		return masterFileRepository.findByIdAndUserIdAndActiveTrue(fileId, userId)
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);
	}

	private HttpHeaders buildDriveHeaders(MasterFile file, MediaType contentType, boolean download) {

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(contentType);
		headers.setCacheControl("private, no-store");
		headers.set("X-Content-Type-Options", "nosniff");

		if (download) {
			headers.setContentDisposition(ContentDisposition.attachment().filename(file.getName()).build());
		} else {
			headers.setContentDisposition(ContentDisposition.inline().filename(file.getName()).build());
		}

		return headers;
	}

	private Date getSessionExpiry(HttpServletRequest request) {

		String authHeader = request.getHeader("Authorization");

		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			String token = authHeader.substring(7);

			try {
				return jwtService.extractExpiration(token);
			} catch (Exception ex) {
				log.warn("Could not extract session expiration.", ex);
			}
		}

		return null;
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

	private MediaType detectMediaType(String fileId) {

		String lower = fileId.toLowerCase();

		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
			return MediaType.IMAGE_JPEG;
		}
		if (lower.endsWith(".png")) {
			return MediaType.IMAGE_PNG;
		}
		if (lower.endsWith(".gif")) {
			return MediaType.IMAGE_GIF;
		}
		if (lower.endsWith(".svg")) {
			return MediaType.valueOf("image/svg+xml");
		}
		if (lower.endsWith(".mp4")) {
			return MediaType.valueOf("video/mp4");
		}
		if (lower.endsWith(".webm")) {
			return MediaType.valueOf("video/webm");
		}
		if (lower.endsWith(".mp3")) {
			return MediaType.valueOf("audio/mpeg");
		}
		if (lower.endsWith(".wav")) {
			return MediaType.valueOf("audio/wav");
		}
		if (lower.endsWith(".pdf")) {
			return MediaType.APPLICATION_PDF;
		}
		if (lower.endsWith(".txt")) {
			return MediaType.TEXT_PLAIN;
		}

		return MediaType.APPLICATION_OCTET_STREAM;
	}

	private String extractFilename(String fileId) {
		if (fileId.contains(".")) {
			return fileId;
		}
		return "file.bin";
	}
}
