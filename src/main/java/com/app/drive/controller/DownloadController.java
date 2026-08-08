package com.app.drive.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import com.app.drive.service.DownloadService;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.storage.factory.StorageFactory;

@RestController
@RequestMapping("/download")
public class DownloadController {

	private final DownloadService service;
	private final MasterFileRepository repository;
	private final StorageFactory storageFactory;

	public DownloadController(DownloadService service, MasterFileRepository repository, StorageFactory storageFactory) {
		this.service = service;
		this.repository = repository;
		this.storageFactory = storageFactory;
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> get(@PathVariable String id, Authentication auth,
			@RequestParam(defaultValue = "false") boolean metadata) {
		MasterFile file = repository.findByIdAndUserId(id, auth.getName())
				.orElseThrow(com.app.core.exception.FileNotFoundException::new);
		if (metadata) return ResponseEntity.ok(file);

		byte[] content = storageFactory.get().download(file.getFileId());
		MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
		try { if (file.getContentType() != null) contentType = MediaType.parseMediaType(file.getContentType()); }
		catch (IllegalArgumentException ignored) { }
		return ResponseEntity.ok()
				.contentType(contentType)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.getName()).build().toString())
				.body(content);
	}
}
