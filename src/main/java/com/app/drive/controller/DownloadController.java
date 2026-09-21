package com.app.drive.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.drive.service.DownloadService;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.share.entity.SharedResource;
import com.app.share.repository.SharedResourceRepository;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;

@RestController
@RequestMapping("/download")
public class DownloadController {

	private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

	private final DownloadService service;
	private final MasterFileRepository repository;
	private final SharedResourceRepository shareRepository;
	private final FileTransferService fileTransferService;
	private final HttpRangeParser httpRangeParser;

	public DownloadController(DownloadService service, MasterFileRepository repository,
			SharedResourceRepository shareRepository, FileTransferService fileTransferService,
			HttpRangeParser httpRangeParser) {

		this.service = service;
		this.repository = repository;
		this.shareRepository = shareRepository;
		this.fileTransferService = fileTransferService;
		this.httpRangeParser = httpRangeParser;
	}

	/**
	 * ============================================================ FILE DOWNLOAD
	 * ============================================================
	 *
	 * This endpoint is intentionally typed as
	 * ResponseEntity<StreamingResponseBody>.
	 *
	 * Do NOT change this back to ResponseEntity<?>.
	 */
	@GetMapping("/{id}")
	public ResponseEntity<StreamingResponseBody> get(@PathVariable String id, Authentication auth,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

		MasterFile file = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName())
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);

		validateDownloadableFile(file);

		MediaType contentType = resolveContentType(file.getContentType());

		/*
		 * -------------------------------------------------------- CHUNKED FILE
		 * --------------------------------------------------------
		 */
		if (fileTransferService.isChunked(file)) {

			return downloadChunkedFile(file, contentType, rangeHeader);
		}

		/*
		 * -------------------------------------------------------- LEGACY FILE
		 * --------------------------------------------------------
		 */
		if (fileTransferService.isLegacy(file)) {

			return downloadLegacyFile(file, contentType);
		}

		throw new IllegalStateException("Storage reference missing for file: " + file.getName());
	}

	/**
	 * ============================================================ METADATA
	 * ============================================================
	 *
	 * Use:
	 *
	 * GET /download/{id}/metadata
	 *
	 * This is intentionally a separate endpoint so the actual download endpoint has
	 * exactly one response-body type.
	 */
	@GetMapping("/{id}/metadata")
	public ResponseEntity<MasterFile> metadata(@PathVariable String id, Authentication auth) {

		MasterFile file = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName())
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);

		return ResponseEntity.ok(file);
	}

	/**
	 * ============================================================ VALIDATION
	 * ============================================================
	 */
	private void validateDownloadableFile(MasterFile file) {

		if (!"FILE".equalsIgnoreCase(file.getDriveType())) {

			throw new IllegalArgumentException("Only files can be downloaded.");
		}

		if (file.getSize() == null || file.getSize() < 0) {

			throw new IllegalStateException("File size is missing or invalid.");
		}
	}

	/**
	 * ============================================================ CHUNKED DOWNLOAD
	 * ============================================================
	 */
	private ResponseEntity<StreamingResponseBody> downloadChunkedFile(MasterFile file, MediaType contentType,
			String rangeHeader) {

		/*
		 * -------------------------------------------------------- EMPTY FILE
		 * --------------------------------------------------------
		 */
		if (file.getSize() == 0) {

			StreamingResponseBody body = outputStream -> {
				// Empty file: nothing to write.
			};

			HttpHeaders headers = createDownloadHeaders(file, contentType);

			headers.setContentLength(0);

			return ResponseEntity.ok().headers(headers).body(body);
		}

		/*
		 * -------------------------------------------------------- RANGE RESOLUTION
		 * --------------------------------------------------------
		 */
		ByteRange resolvedRange;

		boolean partialRequest = rangeHeader != null && !rangeHeader.isBlank();

		try {

			if (!partialRequest) {

				/*
				 * Complete file.
				 */
				resolvedRange = new ByteRange(0L, file.getSize() - 1L);

			} else {

				/*
				 * Partial file.
				 */
				RangeRequest rangeRequest = httpRangeParser.parse(rangeHeader);

				resolvedRange = httpRangeParser.resolve(rangeRequest, file.getSize());
			}

		} catch (IllegalArgumentException ex) {

			return buildRangeNotSatisfiableResponse(file.getSize());
		}

		/*
		 * -------------------------------------------------------- STREAMING RESPONSE
		 * --------------------------------------------------------
		 */
		final ByteRange finalRange = resolvedRange;

		StreamingResponseBody body = outputStream -> {

			fileTransferService.streamRange(file, finalRange, outputStream);

			outputStream.flush();
		};

		HttpHeaders headers = createDownloadHeaders(file, contentType);

		headers.setContentLength(finalRange.getLength());

		/*
		 * -------------------------------------------------------- COMPLETE FILE = 200
		 * --------------------------------------------------------
		 */
		if (!partialRequest) {

			return ResponseEntity.ok().headers(headers).body(body);
		}

		/*
		 * -------------------------------------------------------- PARTIAL FILE = 206
		 * --------------------------------------------------------
		 */
		headers.set(HttpHeaders.CONTENT_RANGE,
				"bytes " + finalRange.getStart() + "-" + finalRange.getEnd() + "/" + file.getSize());

		return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
	}

	/**
	 * ============================================================ LEGACY DOWNLOAD
	 * ============================================================
	 *
	 * Current legacy FileTransferService API returns byte[].
	 *
	 * This keeps compatibility with the existing implementation.
	 */
	private ResponseEntity<StreamingResponseBody> downloadLegacyFile(MasterFile file, MediaType contentType) {

		byte[] content = fileTransferService.downloadLegacy(file.getFileId());

		StreamingResponseBody body = outputStream -> {

			outputStream.write(content);
			outputStream.flush();
		};

		HttpHeaders headers = createDownloadHeaders(file, contentType);

		headers.setContentLength(content.length);

		return ResponseEntity.ok().headers(headers).body(body);
	}

	/**
	 * ============================================================ DOWNLOAD HEADERS
	 * ============================================================
	 */
	private HttpHeaders createDownloadHeaders(MasterFile file, MediaType contentType) {

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(contentType);

		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

		headers.set(HttpHeaders.CONTENT_DISPOSITION, buildAttachment(file));

		return headers;
	}

	/**
	 * ============================================================ HTTP 416
	 * ============================================================
	 */
	private ResponseEntity<StreamingResponseBody> buildRangeNotSatisfiableResponse(long fileSize) {

		HttpHeaders headers = new HttpHeaders();

		headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);

		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

		return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
	}

	/**
	 * ============================================================ CONTENT
	 * DISPOSITION ============================================================
	 */
	private String buildAttachment(MasterFile file) {

		return ContentDisposition.attachment().filename(file.getName()).build().toString();
	}

	/**
	 * ============================================================ CONTENT TYPE
	 * ============================================================
	 */
	private MediaType resolveContentType(String contentTypeValue) {

		if (contentTypeValue == null || contentTypeValue.isBlank()) {

			return MediaType.APPLICATION_OCTET_STREAM;
		}

		try {

			return MediaType.parseMediaType(contentTypeValue);

		} catch (IllegalArgumentException ex) {

			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	/*
	 * ============================================================ BULK DOWNLOAD
	 * ============================================================
	 */

	@PostMapping("/bulk")
	public ResponseEntity<StreamingResponseBody> downloadBulk(@RequestBody List<String> ids, Authentication auth) {

		List<ZipEntryInfo> allEntries = new ArrayList<>();

		for (String id : ids) {

			collectFilesRecursivelyAuth(id, allEntries, auth, "");
		}

		return buildZipResponseStream(allEntries);
	}

	/*
	 * ============================================================ BULK SHARED
	 * DOWNLOAD ============================================================
	 */

	@PostMapping("/bulk/shared")
	public ResponseEntity<StreamingResponseBody> downloadBulkShared(@RequestBody List<String> ids,
			@RequestParam String token) {

		SharedResource share = shareRepository.findByToken(token)
				.orElseThrow(() -> new IllegalArgumentException("Invalid share token"));

		List<String> validRootIds = share.getFileIds();

		if (validRootIds == null || validRootIds.isEmpty()) {

			validRootIds = Collections.singletonList(share.getFileId());
		}

		List<ZipEntryInfo> allFiles = new ArrayList<>();

		for (String id : ids) {

			if (!isUnderSharedRoot(id, validRootIds)) {

				throw new IllegalArgumentException("Access denied: one or more items " + "are not part of this share");
			}

			collectFilesRecursivelyShared(id, allFiles, validRootIds, "");
		}

		return buildZipResponseStream(allFiles);
	}

	private record ZipEntryInfo(MasterFile file, String relativePath) {
	}

	/*
	 * ============================================================ AUTHENTICATED
	 * ZIP TREE ============================================================
	 */

	private void collectFilesRecursivelyAuth(String id, List<ZipEntryInfo> accumulator, Authentication auth,
			String currentPath) {

		MasterFile item = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName()).orElse(null);

		if (item == null) {
			return;
		}

		if ("FILE".equalsIgnoreCase(item.getDriveType())) {

			if (fileTransferService.isChunked(item) || fileTransferService.isLegacy(item)) {

				accumulator.add(new ZipEntryInfo(item, currentPath + item.getName()));

			} else {

				logger.warn("Skipping file '{}' because no " + "storage reference exists", item.getName());
			}

		} else if ("FOLDER".equalsIgnoreCase(item.getDriveType())) {

			String folderPath = currentPath + item.getName() + "/";

			accumulator.add(new ZipEntryInfo(null, folderPath));

			List<MasterFile> children = repository.findByParentIdAndActiveTrue(id);

			for (MasterFile child : children) {

				collectFilesRecursivelyAuth(child.getId(), accumulator, auth, folderPath);
			}
		}
	}

	/*
	 * ============================================================ SHARED ZIP TREE
	 * ============================================================
	 */

	private void collectFilesRecursivelyShared(String id, List<ZipEntryInfo> accumulator, List<String> validRootIds,
			String currentPath) {

		MasterFile item = repository.findById(id).orElse(null);

		if (item == null) {
			return;
		}

		if (!item.isActive()) {
			return;
		}

		if (!isUnderSharedRoot(item.getId(), validRootIds)) {

			return;
		}

		if ("FILE".equalsIgnoreCase(item.getDriveType())) {

			if (fileTransferService.isChunked(item) || fileTransferService.isLegacy(item)) {

				accumulator.add(new ZipEntryInfo(item, currentPath + item.getName()));

			} else {

				logger.warn("Skipping shared file '{}' because " + "no storage reference exists", item.getName());
			}

		} else if ("FOLDER".equalsIgnoreCase(item.getDriveType())) {

			String folderPath = currentPath + item.getName() + "/";

			accumulator.add(new ZipEntryInfo(null, folderPath));

			List<MasterFile> children = repository.findByParentIdAndActiveTrue(id);

			for (MasterFile child : children) {

				collectFilesRecursivelyShared(child.getId(), accumulator, validRootIds, folderPath);
			}
		}
	}

	/*
	 * ============================================================ SHARED ROOT
	 * VALIDATION ============================================================
	 */

	private boolean isUnderSharedRoot(String itemId, List<String> validRootIds) {

		if (itemId == null || validRootIds == null || validRootIds.isEmpty()) {

			return false;
		}

		for (String rootId : validRootIds) {

			if (isUnderSharedRoot(itemId, rootId)) {

				return true;
			}
		}

		return false;
	}

	private boolean isUnderSharedRoot(String itemId, String sharedRootId) {

		if (itemId == null || sharedRootId == null) {

			return false;
		}

		if (itemId.equals(sharedRootId)) {
			return true;
		}

		String currentId = itemId;

		Set<String> visited = new HashSet<>();

		while (currentId != null && !visited.contains(currentId)) {

			visited.add(currentId);

			MasterFile current = repository.findById(currentId).orElse(null);

			if (current == null) {
				return false;
			}

			if (currentId.equals(sharedRootId)) {
				return true;
			}

			String parentId = current.getParentId();

			if (parentId == null) {
				break;
			}

			currentId = parentId;
		}

		return false;
	}

	/*
	 * ============================================================ ZIP STREAM
	 * ============================================================
	 */

	private ResponseEntity<StreamingResponseBody> buildZipResponseStream(List<ZipEntryInfo> entries) {

		StreamingResponseBody stream = outputStream -> {

			try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {

				for (ZipEntryInfo entryInfo : entries) {

					if (entryInfo.file() != null) {

						MasterFile file = entryInfo.file();

						ZipEntry entry = new ZipEntry(entryInfo.relativePath());

						zos.putNextEntry(entry);

						if (file.getSize() != null && file.getSize() > 0) {

							fileTransferService.streamRange(file, new ByteRange(0L, file.getSize() - 1L), zos);
						}

						zos.closeEntry();

					} else {

						ZipEntry entry = new ZipEntry(entryInfo.relativePath());

						zos.putNextEntry(entry);

						zos.closeEntry();
					}

					zos.flush();
				}
			}
		};

		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

		headers.setContentDisposition(ContentDisposition.attachment().filename("download.zip").build());

		return ResponseEntity.ok().headers(headers).body(stream);
	}
}
