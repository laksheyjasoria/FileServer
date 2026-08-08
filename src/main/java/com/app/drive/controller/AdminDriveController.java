package com.app.drive.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;

@RestController
@RequestMapping("/admin/drives")
public class AdminDriveController {

	private final MasterFileRepository repo;
	private static final List<String> ALLOWED_USER_IDS = Arrays.asList(null, "admin");

	public AdminDriveController(MasterFileRepository repo) {
		this.repo = repo;
	}

	// -------------------- READ --------------------
	@GetMapping
	public List<MasterFile> listAll() {
		return repo.findByDriveTypeAndUserIdIn("FOLDER", ALLOWED_USER_IDS);
	}

	@GetMapping("/{id}")
	public MasterFile getById(@PathVariable String id) {
		return repo.findByIdAndDriveTypeAndUserIdIn(id, "FOLDER", ALLOWED_USER_IDS)
				.orElseThrow(() -> new FileNotFoundException("Drive not found or not accessible"));
	}

	@GetMapping("/{id}/contents")
	public List<MasterFile> getContents(@PathVariable String id) {
		repo.findByIdAndDriveTypeAndUserIdIn(id, "FOLDER", ALLOWED_USER_IDS)
				.orElseThrow(() -> new FileNotFoundException("Folder not found or not accessible"));
		return repo.findByParentId(id);
	}

	// -------------------- CREATE --------------------
	@PostMapping
	public ResponseEntity<MasterFile> createRootDrive(@RequestBody CreateDriveRequest request) {
		MasterFile file = new MasterFile();
		file.setName(request.getName());
		file.setDriveType("FOLDER");
		file.setUserId(request.getUserId() != null ? request.getUserId() : "admin");
		file.setParentId(request.getParentId());
		file.setSize(0L);
		file.setAccessType("PUBLIC"); // hardcoded public

		MasterFile saved = repo.save(file);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	@PostMapping("/{id}/nested")
	public ResponseEntity<MasterFile> createNestedFolder(@PathVariable String id,
			@RequestBody CreateDriveRequest request) {
		repo.findByIdAndDriveTypeAndUserIdIn(id, "FOLDER", ALLOWED_USER_IDS)
				.orElseThrow(() -> new FileNotFoundException("Parent folder not found or not accessible"));

		MasterFile file = new MasterFile();
		file.setName(request.getName());
		file.setDriveType("FOLDER");
		file.setUserId(request.getUserId() != null ? request.getUserId() : "admin");
		file.setParentId(id);
		file.setSize(0L);
		file.setAccessType("PUBLIC");

		MasterFile saved = repo.save(file);
		return ResponseEntity.status(HttpStatus.CREATED).body(saved);
	}

	// -------------------- DELETE --------------------
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteDrive(@PathVariable String id) {
		MasterFile file = repo.findByIdAndDriveTypeAndUserIdIn(id, "FOLDER", ALLOWED_USER_IDS)
				.orElseThrow(() -> new FileNotFoundException("Drive not found or not accessible"));
		repo.delete(file);
		return ResponseEntity.noContent().build();
	}

	// ================= DTOs =================
	public static class CreateDriveRequest {
		private String name;
		private String parentId;
		private String userId;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getParentId() {
			return parentId;
		}

		public void setParentId(String parentId) {
			this.parentId = parentId;
		}

		public String getUserId() {
			return userId;
		}

		public void setUserId(String userId) {
			this.userId = userId;
		}
	}
}