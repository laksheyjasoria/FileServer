package com.app.share.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

	// ---------------------- Single Share Creation ----------------------
	public ShareResponse create(CreateShareRequest request, String userId) {
		files.findByIdAndUserId(request.getFileId(), userId).orElseThrow(FileNotFoundException::new);
		SharedResource share = new SharedResource();
		String token = UUID.randomUUID().toString();
		share.setToken(token);
		share.setFileId(request.getFileId());
		share.setCreatedBy(userId);
		share.setPublicAccess(request.isPublicAccess());
		share.setExpiry(request.getExpiry());
		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			share.setPassword(encoder.encode(request.getPassword()));
		}
		share.setPermission(request.getPermission() != null ? request.getPermission() : SharePermission.VIEW_DOWNLOAD);
		repo.save(share);
		return new ShareResponse(props.getFrontendUrl() + "/share/" + token, token);
	}

	// ---------------------- Multi-Share Creation ----------------------
	public ShareResponse createMulti(CreateMultiShareRequest request, String userId) {
		for (String id : request.getFileIds()) {
			files.findByIdAndUserId(id, userId).orElseThrow(FileNotFoundException::new);
		}
		SharedResource share = new SharedResource();
		String token = UUID.randomUUID().toString();
		share.setToken(token);
		share.setFileIds(request.getFileIds());
		share.setCreatedBy(userId);
		share.setPublicAccess(request.isPublicAccess());
		share.setExpiry(request.getExpiry());
		if (request.getPassword() != null && !request.getPassword().isBlank()) {
			share.setPassword(encoder.encode(request.getPassword()));
		}
		share.setPermission(request.getPermission() != null ? request.getPermission() : SharePermission.VIEW_DOWNLOAD);
		repo.save(share);
		return new ShareResponse(props.getFrontendUrl() + "/share/" + token, token);
	}

	// ---------------------- Token Validation ----------------------
	public SharedResource validate(String token, String password) {
		// 1. If token not found -> Return 404 (SHARE_NOT_FOUND)
		SharedResource share = repo.findByToken(token).orElseThrow(() -> new ShareNotFoundException());

		// 2. If expired -> Return 410 (SHARE_EXPIRED)
		if (share.getExpiry() != null && share.getExpiry().isBefore(LocalDateTime.now())) {
			throw new ShareExpiredException();
		}

		// 3. Password check (For PROTECTED shares)
		if (share.getPassword() != null) {
			if (password == null || password.isBlank()) {
				throw new SharePasswordRequiredException();
			}
			if (!encoder.matches(password, share.getPassword())) {
				throw new InvalidSharePasswordException();
			}
		}

		// 4. 🔐 Enforce USER_ONLY authentication (Will throw 401 or 403 if they fail)
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
		String currentUser = auth.getName();
		boolean isAdmin = auth.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_ADMIN"));
		if (!currentUser.equals(share.getCreatedBy()) && !isAdmin) {
			throw new ShareAccessDeniedException("You are not authorized to access this share.");
		}
	}

	// ---------------------- Share Details ----------------------
	public PublicShareResponse details(String token, String password) {
		SharedResource share = validate(token, password);
		boolean isMulti = share.getFileIds() != null && !share.getFileIds().isEmpty();

		String driveName;
		String driveType;

		if (isMulti) {
			driveName = "Shared Items (" + share.getFileIds().size() + " items)";
			driveType = "MULTI";
		} else {
			MasterFile file = files.findById(share.getFileId()).orElseThrow(FileNotFoundException::new);
			driveName = file.getName();
			driveType = file.getDriveType();
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
				share.getCreatedAt(), finalPermission);
	}

	// ---------------------- Single File Fetch ----------------------
	public MasterFile file(String token, String password) {
		SharedResource share = validate(token, password);
		return files.findById(share.getFileId()).orElseThrow(FileNotFoundException::new);
	}

	// ---------------------- Folder Navigation ----------------------
	public List<MasterFile> folderContents(String token, String password) {
		return folderContents(token, password, null);
	}

	public List<MasterFile> folderContents(String token, String password, String folderId) {
		SharedResource share = validate(token, password);
		boolean isMulti = share.getFileIds() != null && !share.getFileIds().isEmpty();

		if (folderId == null) {
			// 📂 ROOT OF THE SHARE
			if (isMulti) {
				// Multi-share: return all the root items directly
				return files.findAllById(share.getFileIds());
			} else {
				// Single Folder share: return the children of that folder
				MasterFile folder = files.findById(share.getFileId()).orElseThrow(FileNotFoundException::new);
				if (!"FOLDER".equalsIgnoreCase(folder.getDriveType())) {
					throw new RuntimeException("The shared item is not a folder.");
				}
				return files.findByParentId(folder.getId());
			}
		} else {
			// 📂 NAVIGATING INTO A SUB-FOLDER
			// Security check: Ensure the target folder actually belongs to this share
			if (!isUnderSharedRoot(folderId, share)) {
				throw new ShareAccessDeniedException("You are not authorized to access this folder.");
			}

			MasterFile folder = files.findById(folderId).orElseThrow(FileNotFoundException::new);
			if (!"FOLDER".equalsIgnoreCase(folder.getDriveType())) {
				throw new RuntimeException("The requested item is not a folder.");
			}
			return files.findByParentId(folder.getId());
		}
	}

	// ---------------------- Permission Helpers ----------------------
	private boolean isUnderSharedRoot(String itemId, SharedResource share) {
		// If it's a multi-share, we must check against all root IDs
		if (share.getFileIds() != null && !share.getFileIds().isEmpty()) {
			for (String rootId : share.getFileIds()) {
				if (isUnderSharedRoot(itemId, rootId))
					return true;
			}
			return false;
		}
		// Single share: just check against the one root ID
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
			if (current.getParentId() == null)
				break;
			currentId = current.getParentId();
			if (currentId.equals(sharedRootId))
				return true;
		}
		return false;
	}
}