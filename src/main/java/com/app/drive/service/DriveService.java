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

	// ============================================================
	// SOFT DELETE (move to trash) – OPTIMIZED
	// ============================================================
	@Transactional
	public void softDelete(String fileId, String userId) {

		MasterFile file = repo.findByIdAndUserId(fileId, userId).orElseThrow(() -> {
			return new FileNotFoundException();
		});

		// If it's a folder, get all descendants and bulk soft-delete them
		if ("FOLDER".equals(file.getDriveType()) || "ROOT".equals(file.getDriveType())) {
			List<String> descendantIds = repo.findAllDescendantIds(fileId);
			if (!descendantIds.isEmpty()) {
				repo.softDeleteAllByIds(descendantIds);
			}
		}

		// Soft delete the item itself
		file.setActive(false);
		file.setDeletedAt(LocalDateTime.now());
		repo.save(file);
	}

	// ============================================================
	// RESTORE – OPTIMIZED
	// ============================================================
	@Transactional
	public void restore(String fileId, String userId) {

		MasterFile file = repo.findByIdAndUserIdAndActiveFalse(fileId, userId).orElseThrow(() -> {
			return new FileNotFoundException();
		});

		// If it's a folder, get all descendants and bulk restore them
		if ("FOLDER".equals(file.getDriveType()) || "ROOT".equals(file.getDriveType())) {
			List<String> descendantIds = repo.findAllDescendantIds(fileId);
			if (!descendantIds.isEmpty()) {
				repo.restoreAllByIds(descendantIds);
			}
		}

		// Restore the item itself
		file.setActive(true);
		file.setDeletedAt(null);
		repo.save(file);
	}

	// ============================================================
	// LIST TRASH
	// ============================================================
	public List<MasterFile> listTrash(String userId) {
		return repo.findByUserIdAndActiveFalseAndDeletedAtIsNotNull(userId);
	}

	// ============================================================
	// PERMANENT DELETE
	// ============================================================
	@Transactional
	public void permanentDelete(String fileId, String userId) {

		Optional<MasterFile> anyFile = repo.findById(fileId);
		if (anyFile.isEmpty()) {
			throw new FileNotFoundException();
		}

		MasterFile file = anyFile.get();

		if (!file.getUserId().equals(userId)) {
			throw new SecurityException("Access denied");
		}

		if (file.isActive()) {
			throw new IllegalStateException("File is not in trash. Please delete it first.");
		}

		// 1. Delete shares for the entire folder tree (if folder)
		// For a single file, delete its share directly.
		if ("FOLDER".equals(file.getDriveType()) || "ROOT".equals(file.getDriveType())) {
			deleteSharesRecursively(fileId);
		} else {
			shareRepo.deleteByFileId(fileId);
		}

		// 2. Delete physical storage (if it's a file)
		if ("FILE".equals(file.getDriveType()) && file.getFileId() != null) {
			deleteService.delete(fileId);
		}

		// 3. Hard delete the root item and all descendants (if folder)
		if ("FOLDER".equals(file.getDriveType()) || "ROOT".equals(file.getDriveType())) {
			List<String> descendantIds = repo.findAllDescendantIds(fileId);
			if (!descendantIds.isEmpty()) {
				repo.deleteAllById(descendantIds);
			}
		}

		// Finally, delete the root item
		repo.delete(file);
	}

	// ---------- RECURSIVE SHARE DELETION (for folders) ----------
	private void deleteSharesRecursively(String folderId) {
		List<String> descendantIds = repo.findAllDescendantIds(folderId);
		List<String> allIds = new ArrayList<>();
		allIds.add(folderId);
		allIds.addAll(descendantIds);
		if (!allIds.isEmpty()) {
			shareRepo.deleteByFileIdIn(allIds);
		}
	}

	// ============================================================
	// EMPTY TRASH
	// ============================================================
	@Transactional
	public void emptyTrash(String userId) {
		List<MasterFile> trashed = repo.findByUserIdAndActiveFalseAndDeletedAtIsNotNull(userId);
		if (trashed.isEmpty()) {
			return;
		}

		for (MasterFile file : trashed) {
			// Delete shares
			if ("FOLDER".equals(file.getDriveType()) || "ROOT".equals(file.getDriveType())) {
				deleteSharesRecursively(file.getId());
			} else {
				shareRepo.deleteByFileId(file.getId());
			}

			// Delete storage (if file)
			if ("FILE".equals(file.getDriveType()) && file.getFileId() != null) {
				deleteService.delete(file.getId());
			}
		}

		// Bulk delete all records
		repo.deleteAllInBatch(trashed);
	}
}