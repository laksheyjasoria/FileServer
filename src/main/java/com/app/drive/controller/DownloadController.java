package com.app.drive.controller;

import java.io.OutputStream;
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
import com.app.storage.factory.StorageFactory;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRange;
import com.app.transfer.HttpRangeParser;

@RestController
@RequestMapping("/download")
public class DownloadController {

	private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

	private final DownloadService service;
	private final MasterFileRepository repository;
	private final StorageFactory storageFactory;
	private final SharedResourceRepository shareRepository;
	private final FileTransferService fileTransferService;
	private final HttpRangeParser httpRangeParser;

	public DownloadController(DownloadService service, MasterFileRepository repository, StorageFactory storageFactory,
			SharedResourceRepository shareRepository, FileTransferService fileTransferService,
			HttpRangeParser httpRangeParser) {

		this.service = service;
		this.repository = repository;
		this.storageFactory = storageFactory;
		this.shareRepository = shareRepository;
		this.fileTransferService = fileTransferService;
		this.httpRangeParser = httpRangeParser;
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable String id, Authentication auth,
			@RequestParam(defaultValue = "false") boolean metadata,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

		MasterFile file = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName())
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);

		if (metadata) {
			return ResponseEntity.ok(file);
		}

		if (!"FILE".equalsIgnoreCase(file.getDriveType())) {

			throw new IllegalArgumentException("Only files can be downloaded.");
		}

		if (file.getSize() == null || file.getSize() < 0) {

			throw new IllegalStateException("File size is missing or invalid.");
		}

		MediaType contentType = resolveContentType(file.getContentType());

		/*
		 * ========================================================= CHUNKED FILE
		 * =========================================================
		 */
		if (fileTransferService.isChunked(file)) {

			if (file.getSize() == 0) {
				return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.ACCEPT_RANGES, "bytes")
						.header(HttpHeaders.CONTENT_DISPOSITION, buildAttachment(file)).contentLength(0)
						.body((StreamingResponseBody) outputStream -> {
						});
			}

			HttpRange resolvedRange;

			try {

				if (rangeHeader == null || rangeHeader.isBlank()) {

					resolvedRange = new HttpRange(0, file.getSize() - 1);

				} else {

					resolvedRange = httpRangeParser.parse(rangeHeader, file.getSize());
				}

			} catch (IllegalArgumentException ex) {

				return buildRangeNotSatisfiableResponse(file.getSize());
			}

			ByteRange byteRange = new ByteRange(resolvedRange.getStart(), resolvedRange.getEnd());

			StreamingResponseBody body = outputStream -> fileTransferService.streamChunkedRange(file, byteRange,
					outputStream);

			HttpHeaders headers = new HttpHeaders();

			headers.setContentType(contentType);

			headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

			headers.set(HttpHeaders.CONTENT_DISPOSITION, buildAttachment(file));

			headers.setContentLength(byteRange.getLength());

			/*
			 * No Range header means a normal 200 response. A valid Range header means 206
			 * Partial Content.
			 */
			if (rangeHeader == null || rangeHeader.isBlank()) {

				return ResponseEntity.ok().headers(headers).body(body);
			}

			headers.set(HttpHeaders.CONTENT_RANGE,
					"bytes " + byteRange.getStart() + "-" + byteRange.getEnd() + "/" + file.getSize());

			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
		}

		/*
		 * ========================================================= LEGACY FILE
		 * =========================================================
		 *
		 * Existing files continue using the old storage path.
		 */
		if (!fileTransferService.isLegacy(file)) {

			throw new RuntimeException("Storage reference missing for file: " + file.getName());
		}

		byte[] content = fileTransferService.downloadLegacy(file.getFileId());

		/*
		 * Legacy range support is intentionally not implemented through the old byte[]
		 * storage path.
		 *
		 * The chunked transfer architecture owns production Range handling. Legacy
		 * files retain backward compatibility.
		 */
		return ResponseEntity.ok().contentType(contentType)
				.header(HttpHeaders.CONTENT_DISPOSITION, buildAttachment(file))
				.header(HttpHeaders.ACCEPT_RANGES, "bytes").contentLength(content.length).body(content);
	}

	private ResponseEntity<Void> buildRangeNotSatisfiableResponse(long fileSize) {

		HttpHeaders headers = new HttpHeaders();

		headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);

		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

		return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
	}

	private String buildAttachment(MasterFile file) {

		return ContentDisposition.attachment().filename(file.getName()).build().toString();
	}

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

	@PostMapping("/bulk")
	public ResponseEntity<StreamingResponseBody> downloadBulk(@RequestBody List<String> ids, Authentication auth) {

		List<ZipEntryInfo> allEntries = new ArrayList<>();

		for (String id : ids) {
			collectFilesRecursivelyAuth(id, allEntries, auth, "");
		}

		return buildZipResponseStream(allEntries);
	}

	@PostMapping("/bulk/shared")
	public ResponseEntity<StreamingResponseBody> downloadBulkShared(@RequestBody List<String> ids,
			@RequestParam String token) {

		SharedResource share = shareRepository.findByToken(token)
				.orElseThrow(() -> new RuntimeException("Invalid share token"));

		List<String> validRootIds = share.getFileIds();

		if (validRootIds == null || validRootIds.isEmpty()) {

			validRootIds = Collections.singletonList(share.getFileId());
		}

		List<ZipEntryInfo> allFiles = new ArrayList<>();

		for (String id : ids) {

			if (!isUnderSharedRoot(id, validRootIds)) {

				throw new RuntimeException("Access denied: one or more items " + "are not part of this share");
			}

			collectFilesRecursivelyShared(id, allFiles, validRootIds, "");
		}

		return buildZipResponseStream(allFiles);
	}

	private record ZipEntryInfo(MasterFile file, String relativePath) {
	}

	private void collectFilesRecursivelyAuth(String id, List<ZipEntryInfo> accumulator, Authentication auth,
			String currentPath) {

		MasterFile item = repository.findByIdAndUserIdAndActiveTrue(id, auth.getName()).orElse(null);

		if (item == null) {
			return;
		}

		if ("FILE".equalsIgnoreCase(item.getDriveType())) {

			if (item.getFileId() != null && !item.getFileId().isBlank()) {

				accumulator.add(new ZipEntryInfo(item, currentPath + item.getName()));

			} else {
				logger.warn("Skipping file '{}' because fileId is missing", item.getName());
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

			if (item.getFileId() != null && !item.getFileId().isBlank()) {

				accumulator.add(new ZipEntryInfo(item, currentPath + item.getName()));

			} else {
				logger.warn("Skipping shared file '{}' because fileId is missing", item.getName());
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

	private ResponseEntity<StreamingResponseBody> buildZipResponseStream(List<ZipEntryInfo> entries) {

		StreamingResponseBody stream = (OutputStream outputStream) -> {

			try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {

				for (ZipEntryInfo entryInfo : entries) {

					if (entryInfo.file() != null) {

						byte[] content = storageFactory.get().download(entryInfo.file().getFileId());

						ZipEntry entry = new ZipEntry(entryInfo.relativePath());

						zos.putNextEntry(entry);

						zos.write(content);

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