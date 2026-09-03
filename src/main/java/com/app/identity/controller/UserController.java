package com.app.identity.controller;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.identity.dto.GoogleIdTokenRequest;
import com.app.identity.dto.UserSearchResult;
import com.app.identity.entity.Friend;
import com.app.identity.entity.User;
import com.app.identity.enums.FriendStatus;
import com.app.identity.repository.UserRepository;
import com.app.identity.service.AuthService;
import com.app.identity.service.FriendService;
import com.app.identity.service.PrivacyService;
import com.app.identity.service.UserManagementService;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserRepository userRepo;
	private final UserManagementService userManagementService;
	private final PrivacyService privacyService;
	private AuthService authService;
	private FriendService friendService;

	public UserController(UserRepository userRepo, UserManagementService userManagementService,
			PrivacyService privacyService, AuthService authService,FriendService friendService) {
		this.userRepo = userRepo;
		this.userManagementService = userManagementService;
		this.privacyService = privacyService;
		this.authService = authService;
		this.friendService=friendService;
	}

	// Existing search endpoint
	@GetMapping("/search")
	@Transactional(readOnly = true)
	public List<UserSearchResult> searchUsers(@RequestParam("q") String query, Authentication auth) {
		if (query == null || query.isBlank()) {
			return List.of();
		}

		User currentUser = userManagementService.getCurrentUser(auth.getName());
		List<User> users = userRepo.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(query, query);
		List<User> allowedUsers = privacyService.filterAllowedRecipients(currentUser, users);

		return allowedUsers.stream()
	            .limit(10)
	            .map(u -> {
	                String status = friendService.getFriendRequestStatus(currentUser, u);
	                return new UserSearchResult(u.getId(), u.getEmail(), u.getName(), u.getPhotoUrl(), status);
	            })
	            .collect(Collectors.toList());
	}

	// NEW: Get current user profile
	@GetMapping("/me")
	public User getCurrentUser(Authentication authentication) {
		return userManagementService.getCurrentUser(authentication.getName());
	}

	// NEW: Soft delete own account
	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteMe(Authentication authentication) {
		userManagementService.softDeleteUser(authentication.getName());
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/me/sync-google")
	public ResponseEntity<Void> syncGoogleProfile(@RequestBody GoogleIdTokenRequest request, Authentication auth) {
		authService.syncProfileWithGoogle(request.getIdToken(), auth.getName());
		return ResponseEntity.ok().build();
	}

}