package com.app.drive.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.share.repository.SharedResourceRepository;

@Service
public class DriveService {

	private final MasterFileRepository repo;
	private final SharedResourceRepository shareRepo;
	private final DeleteService deleteService;

	public DriveService(MasterFileRepository repo, SharedResourceRepository shareRepo, DeleteService deleteService) {
		this.repo = repo;
		this.shareRepo = shareRepo;
		this.deleteService = deleteService;
	}

	// ---------- LISTING (active only) ----------
	public List<MasterFile> list(String userId) {
		return repo.findByUserId(userId);
	}

	public List<MasterFile> listRoot(String userId) {
		List<MasterFile> files = repo.findByUserIdAndParentIdIsNullAndActiveTrue(userId);
		for (MasterFile file : files) {
			if ("FOLDER".equals(file.getDriveType())) {
				Long count = repo.countByParentIdAndActiveTrue(file.getId());
				file.setChildrenCount(count.intValue());
			}
		}
		return files;
	}

	public List<MasterFile> listContents(String userId, String parentId) {
		requireOwned(parentId, userId);
		List<MasterFile> files = repo.findByUserIdAndParentIdAndActiveTrue(userId, parentId);
		for (MasterFile file : files) {
			if ("FOLDER".equals(file.getDriveType())) {
				Long count = repo.countByParentIdAndActiveTrue(file.getId());
				file.setChildrenCount(count.intValue());
			}
		}
		return files;
	}

	public MasterFile get(String userId, String id) {
		return requireOwned(id, userId);
	}

	private MasterFile requireOwned(String id, String userId) {
		return repo.findByIdAndUserIdAndActiveTrue(id, userId).orElseThrow(FileNotFoundException::new);
	}

	@Transactional
	public void softDelete(String fileId, String userId) {
		MasterFile file = repo.findByIdAndUserId(fileId, userId).orElseThrow(() -> {
			return new FileNotFoundException();
		});
		file.setActive(false);
		file.setDeletedAt(LocalDateTime.now());
		repo.save(file);
	}

	public List<MasterFile> listTrash(String userId) {
		return repo.findByUserIdAndActiveFalseAndDeletedAtIsNotNull(userId);
	}

	public void restore(String fileId, String userId) {
		MasterFile file = repo.findByIdAndUserIdAndActiveFalse(fileId, userId).orElseThrow(FileNotFoundException::new);
		file.setActive(true);
		file.setDeletedAt(null);
		repo.save(file);
	}

	// ---------- PERMANENT DELETE ----------
//	@Transactional
//	public void permanentDelete(String fileId, String userId) {
//		MasterFile file = repo.findByIdAndUserIdAndActiveFalse(fileId, userId).orElseThrow(FileNotFoundException::new);
//
//		// 1. Delete all share tokens in the entire folder tree (bulk operation)
//		deleteSharesRecursively(fileId);
//
//		// 2. Delete the actual storage file (if it's a file, not a folder)
//		if ("FILE".equals(file.getDriveType()) && file.getFileId() != null) {
//			deleteService.delete(file.getFileId());
//		}
//
//		// 3. Delete the database record
//		repo.delete(file);
//	}

	@Transactional
	public void permanentDelete(String fileId, String userId) {

		Optional<MasterFile> anyFile = repo.findById(fileId);
		if (anyFile.isEmpty()) {
			throw new FileNotFoundException();
		}

		MasterFile file = anyFile.get();
		// 2. Check ownership
		if (!file.getUserId().equals(userId)) {
			throw new SecurityException("Access denied");
		}

		// 3. Check if it's actually in trash (active == false)
		if (file.isActive()) {
			throw new IllegalStateException("File is not in trash. Please delete it first.");
		}

		// 4. Proceed with permanent deletion Delete shares
		deleteSharesRecursively(fileId);

		// Delete storage (if file)
		if ("FILE".equals(file.getDriveType()) && file.getFileId() != null) {
			deleteService.delete(fileId);
		}

		// Hard delete
		repo.delete(file);
	}

	// ---------- EMPTY TRASH (bulk) ----------
	@Transactional
	public void emptyTrash(String userId) {
		List<MasterFile> trashed = repo.findByUserIdAndActiveFalseAndDeletedAtIsNotNull(userId);

		// Handle share cleanup and physical storage deletion
		for (MasterFile file : trashed) {
			// Bulk delete all share tokens in each folder tree
			deleteSharesRecursively(file.getId());

			// Delete physical storage for files
			if ("FILE".equals(file.getDriveType()) && file.getFileId() != null) {
				deleteService.delete(file.getId());
			}
		}

		// Bulk delete all database records at once instead of one by one
		repo.deleteAllInBatch(trashed);
	}

	// ---------- RECURSIVE BULK CLEANUP (UPDATED) ----------
	private void deleteSharesRecursively(String folderId) {
		// Get all descendant IDs in ONE database query (JPA Native CTE)
		List<String> descendantIds = repo.findAllDescendantIds(folderId);

		// Create a list including the root folder itself
		List<String> allIds = new ArrayList<>();
		allIds.add(folderId);
		allIds.addAll(descendantIds);

		// Delete all shares for the entire folder tree in ONE bulk DB call
		if (!allIds.isEmpty()) {
			shareRepo.deleteByFileIdIn(allIds);
		}
	}
}