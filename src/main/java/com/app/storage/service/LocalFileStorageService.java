package com.app.storage.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.exception.StorageException;

import jakarta.annotation.PostConstruct;

@Service
public class LocalFileStorageService implements StorageService {

	private static final Path DEFAULT_UPLOAD_DIR = Paths.get("uploads").toAbsolutePath().normalize();

	private final Path uploadDir;

	public LocalFileStorageService() {
		this(DEFAULT_UPLOAD_DIR);
	}

	public LocalFileStorageService(Path uploadDir) {
		this.uploadDir = uploadDir;
	}

	@PostConstruct
	public void init() {
		try {
			Files.createDirectories(uploadDir);
		} catch (IOException e) {
			throw new StorageException("Unable to initialize local upload directory.");
		}
	}

	@Override
	public String upload(MultipartFile file) {
		try {
			String safeName = UUID.randomUUID() + "_" + file.getOriginalFilename();
			Path target = uploadDir.resolve(safeName);
			Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
			return safeName;
		} catch (IOException e) {
			throw new StorageException("Local upload failed: " + e.getMessage());
		}
	}

	@Override
	public String upload(byte[] data, String fileName) {
		if (data == null || data.length == 0) {
			throw new StorageException("Upload data cannot be null or empty.");
		}
		try {
			String safeName = UUID.randomUUID() + "_" + fileName;
			Path target = uploadDir.resolve(safeName);
			Files.write(target, data);
			return safeName;
		} catch (IOException e) {
			throw new StorageException("Local upload failed: " + e.getMessage());
		}
	}

	@Override
	public byte[] download(String fileId) {
		try {
			return Files.readAllBytes(uploadDir.resolve(fileId));
		} catch (IOException e) {
			throw new StorageException("Local download failed: " + e.getMessage());
		}
	}
}
