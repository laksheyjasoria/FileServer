package com.app.share.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.app.config.AppProperties;
import com.app.core.exception.FileNotFoundException;
import com.app.core.exception.InvalidSharePasswordException;
import com.app.core.exception.ShareAccessDeniedException;
import com.app.core.exception.ShareAuthenticationRequiredException;
import com.app.core.exception.ShareExpiredException;
import com.app.core.exception.ShareNotFoundException;
import com.app.core.exception.SharePasswordRequiredException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.share.dto.CreateMultiShareRequest;
import com.app.share.dto.CreateShareRequest;
import com.app.share.dto.PublicShareResponse;
import com.app.share.dto.ShareResponse;
import com.app.share.entity.SharePermission;
import com.app.share.entity.SharedResource;
import com.app.share.repository.SharedResourceRepository;

@Service
public class ShareService {

	private final SharedResourceRepository repo;
	private final PasswordEncoder encoder;
	private final AppProperties props;
	private final MasterFileRepository files;

	public ShareService(SharedResourceRepository repo, PasswordEncoder encoder, AppProperties props,
			MasterFileRepository files) {
		this.repo = repo;
		this.encoder = encoder;
		this.props = props;
		this.files = files;
	}

	// ================= CREATE =================

	public ShareResponse create(CreateShareRequest request, String userId) {
		// ✅ Check file exists and is ACTIVE
		MasterFile file = files.findByIdAndUserIdAndActiveTrue(request.getFileId(), userId)
				.orElseThrow(FileNotFoundException::new);
		// If file is a folder, we might still want to share it; active check is enough.
		SharedResource share = new SharedResource();
		String token = UUID.randomUUID().toString();
		share.setToken(token);
		share.setFileId(request.getFileId());
		share.setCreatedBy(userId);
		share.setPublicAccess(request.isPublicAccess());
		share.setExpiry(request.getExpiry());
		share.setPermission(request.getPermission() != null ? request.getPermission() : SharePermission.VIEW_DOWNLOAD);
		share.setAllowedUsers(request.getAllowedUsers());
		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			share.setPassword(encoder.encode(request.getPassword()));
		}
		repo.save(share);
		return new ShareResponse(props.getFrontendUrl() + "/share/" + token, token);
	}

	public ShareResponse createMulti(CreateMultiShareRequest request, String userId) {
		// ✅ Check each file is active
		for (String id : request.getFileIds()) {
			files.findByIdAndUserIdAndActiveTrue(id, userId).orElseThrow(FileNotFoundException::new);
		}
		SharedResource share = new SharedResource();
		String token = UUID.randomUUID().toString();
		share.setToken(token);
		share.setFileIds(request.getFileIds());
		share.setCreatedBy(userId);
		share.setPublicAccess(request.isPublicAccess());
		share.setExpiry(request.getExpiry());
		share.setPermission(request.getPermission() != null ? request.getPermission() : SharePermission.VIEW_DOWNLOAD);
		share.setAllowedUsers(request.getAllowedUsers());
		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			share.setPassword(encoder.encode(request.getPassword()));
		}
		repo.save(share);
		return new ShareResponse(props.getFrontendUrl() + "/share/" + token, token);
	}

	// ================= VALIDATE =================

	public SharedResource validate(String token, String password) {
		SharedResource share = repo.findByToken(token).orElseThrow(ShareNotFoundException::new);
		if (share.getExpiry() != null && share.getExpiry().isBefore(LocalDateTime.now())) {
			throw new ShareExpiredException();
		}
		if (share.getPassword() != null) {
			if (password == null || password.isBlank()) {
				throw new SharePasswordRequiredException();
			}
			if (!encoder.matches(password, share.getPassword())) {
				throw new InvalidSharePasswordException();
			}
		}
		checkUserOnlyAccess(share);
		return share;
	}

	private void checkUserOnlyAccess(SharedResource share) {
		boolean isUserOnly = share.getPassword() == null && !share.isPublicAccess();
		if (!isUserOnly) {
			return;
		}

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();

		if (auth == null || !auth.isAuthenticated()) {
			throw new ShareAuthenticationRequiredException(
					"This share is restricted to registered users. Please log in.");
		}

		String currentUserTrimmed = auth.getName().trim();
		boolean isAdmin = auth.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));

		boolean isOwner = share.getCreatedBy() != null
				&& share.getCreatedBy().trim().equalsIgnoreCase(currentUserTrimmed);

		boolean isAllowedUser = share.getAllowedUsers() != null && share.getAllowedUsers().stream()
				.anyMatch(email -> email != null && email.trim().equalsIgnoreCase(currentUserTrimmed));

		if (!isOwner && !isAdmin && !isAllowedUser) {
			throw new ShareAccessDeniedException("You are not authorized to access this share.");
		}
	}

	// ================= DETAILS =================

	public PublicShareResponse details(String token, String password) {
		SharedResource share = validate(token, password);
		boolean isMulti = share.getFileIds() != null && !share.getFileIds().isEmpty();

		String driveName;
		String driveType;
		Long fileSize = 0L;

		if (isMulti) {
			// ✅ Filter only active files, skip inactive ones (they are soft-deleted)
			List<MasterFile> activeFiles = share.getFileIds().stream()
					.map(id -> files.findByIdAndUserIdAndActiveTrue(id, share.getCreatedBy()).orElse(null))
					.filter(f -> f != null).collect(Collectors.toList());
			if (activeFiles.isEmpty()) {
				throw new FileNotFoundException("No active files in this share");
			}
			driveName = "Shared Items (" + activeFiles.size() + " items)";
			driveType = "MULTI";
			fileSize = activeFiles.stream().filter(f -> f.getSize() != null).mapToLong(MasterFile::getSize).sum();
		} else {
			// ✅ Single file: must be active
			MasterFile file = files.findByIdAndUserIdAndActiveTrue(share.getFileId(), share.getCreatedBy())
					.orElseThrow(FileNotFoundException::new);
			driveName = file.getName();
			driveType = file.getDriveType();
			fileSize = file.getSize() != null ? file.getSize() : 0L;
		}

		String shareType;
		if (share.getPassword() != null) {
			shareType = "PROTECTED";
		} else if (share.isPublicAccess()) {
			shareType = "PUBLIC";
		} else {
			shareType = "USER_ONLY";
		}

		SharePermission finalPermission = share.getPermission();
		if (finalPermission == null) {
			finalPermission = SharePermission.VIEW_DOWNLOAD;
		}

		return new PublicShareResponse(share.getToken(), driveName, driveType, shareType, share.getExpiry(),
				share.getCreatedAt(), finalPermission, share.getCreatedBy(), fileSize);
	}

	// ================= FILE / FOLDER CONTENTS =================

	public MasterFile file(String token, String password) {
		SharedResource share = validate(token, password);
		// ✅ Ensure file is active
		return files.findByIdAndUserIdAndActiveTrue(share.getFileId(), share.getCreatedBy())
				.orElseThrow(FileNotFoundException::new);
	}

	public List<MasterFile> folderContents(String token, String password) {
		return folderContents(token, password, null);
	}

	public List<MasterFile> folderContents(String token, String password, String folderId) {
		SharedResource share = validate(token, password);
		boolean isMulti = share.getFileIds() != null && !share.getFileIds().isEmpty();

		if (folderId == null) {
			if (isMulti) {
				// ✅ Return only active files
				return share.getFileIds().stream()
						.map(id -> files.findByIdAndUserIdAndActiveTrue(id, share.getCreatedBy()).orElse(null))
						.filter(f -> f != null).collect(Collectors.toList());
			} else {
				MasterFile folder = files.findByIdAndUserIdAndActiveTrue(share.getFileId(), share.getCreatedBy())
						.orElseThrow(FileNotFoundException::new);
				if (!"FOLDER".equalsIgnoreCase(folder.getDriveType())) {
					throw new RuntimeException("The shared item is not a folder.");
				}
				// ✅ Return active children
				return files.findByParentIdAndActiveTrue(folder.getId());
			}
		} else {
			if (!isUnderSharedRoot(folderId, share)) {
				throw new ShareAccessDeniedException("You are not authorized to access this folder.");
			}
			MasterFile folder = files.findByIdAndUserIdAndActiveTrue(folderId, share.getCreatedBy())
					.orElseThrow(FileNotFoundException::new);
			if (!"FOLDER".equalsIgnoreCase(folder.getDriveType())) {
				throw new RuntimeException("The requested item is not a folder.");
			}
			// ✅ Return active children
			return files.findByParentIdAndActiveTrue(folder.getId());
		}
	}

	// ================= SHARED WITH ME =================

	public List<PublicShareResponse> getSharedWithMe(String currentUserEmail) {
		List<SharedResource> shares = repo.findByPublicAccessFalseAndPasswordIsNull();
		String currentUserTrimmed = currentUserEmail.trim();

		return shares.stream().filter(share -> {
			if (share.getExpiry() != null && share.getExpiry().isBefore(LocalDateTime.now())) {
				return false;
			}
			boolean isOwner = share.getCreatedBy() != null
					&& share.getCreatedBy().trim().equalsIgnoreCase(currentUserTrimmed);
			boolean isAllowed = share.getAllowedUsers() != null && share.getAllowedUsers().stream()
					.anyMatch(email -> email != null && email.trim().equalsIgnoreCase(currentUserTrimmed));
			return isOwner || isAllowed;
		}).map(share -> {
			try {
				return details(share.getToken(), null);
			} catch (FileNotFoundException e) {
				// If the file is no longer active, skip this share
				return null;
			}
		}).filter(resp -> resp != null).collect(Collectors.toList());
	}

	// ================= SHARED BY ME =================

	public List<PublicShareResponse> getSharedByMe(String currentUserEmail) {
		List<SharedResource> shares = repo.findByCreatedBy(currentUserEmail);

		return shares.stream().filter(share -> {
			if (share.getExpiry() != null && share.getExpiry().isBefore(LocalDateTime.now())) {
				return false;
			}
			return true;
		}).map(share -> {
			try {
				return details(share.getToken(), null);
			} catch (FileNotFoundException e) {
				// If the file is no longer active, skip this share
				return null;
			}
		}).filter(resp -> resp != null).collect(Collectors.toList());
	}

	public void deleteShare(String token, String currentUserEmail) {
		SharedResource share = repo.findByToken(token).orElseThrow(ShareNotFoundException::new);
		if (!share.getCreatedBy().equals(currentUserEmail)) {
			throw new ShareAccessDeniedException("You are not the owner of this share.");
		}
		repo.delete(share);
	}

	// ================= RECURSIVE HELPERS =================

	private boolean isUnderSharedRoot(String itemId, SharedResource share) {
		if (share.getFileIds() != null && !share.getFileIds().isEmpty()) {
			for (String rootId : share.getFileIds()) {
				if (isUnderSharedRoot(itemId, rootId))
					return true;
			}
			return false;
		}
		return isUnderSharedRoot(itemId, share.getFileId());
	}

	private boolean isUnderSharedRoot(String itemId, String sharedRootId) {
		if (itemId.equals(sharedRootId))
			return true;
		String currentId = itemId;
		Set<String> visited = new HashSet<>();
		while (currentId != null && !visited.contains(currentId)) {
			visited.add(currentId);
			MasterFile current = files.findById(currentId).orElse(null);
			if (current == null)
				return false;
			if (currentId.equals(sharedRootId))
				return true;
			String parentId = current.getParentId();
			if (parentId == null)
				break;
			currentId = parentId;
		}
		return false;
	}

	// ================= VALIDATION HELPER =================

	public void validateFileInShare(String token, String password, String fileId) {
		SharedResource share = validate(token, password);
		if (fileId == null || fileId.isBlank())
			return;

		boolean isAuthorized = false;
		if (share.getFileIds() != null && share.getFileIds().contains(fileId)) {
			isAuthorized = true;
		} else if (share.getFileId() != null && share.getFileId().equals(fileId)) {
			isAuthorized = true;
		} else {
			isAuthorized = isUnderSharedRoot(fileId, share);
		}
		if (!isAuthorized) {
			throw new ShareAccessDeniedException("This file is not part of the shared resource.");
		}
		// Also check that the file is active
		files.findByIdAndUserIdAndActiveTrue(fileId, share.getCreatedBy()).orElseThrow(FileNotFoundException::new);
	}
}