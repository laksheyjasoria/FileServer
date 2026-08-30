package com.app.identity.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.identity.dto.UserSearchResult;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;
import com.app.identity.service.UserManagementService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepo;
    private final UserManagementService userManagementService;

    public UserController(UserRepository userRepo, UserManagementService userManagementService) {
        this.userRepo = userRepo;
        this.userManagementService = userManagementService;
    }

    // Existing search endpoint
    @GetMapping("/search")
    public List<UserSearchResult> searchUsers(@RequestParam("q") String query, Authentication auth) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<User> users = userRepo.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(query, query);
        return users.stream()
                .limit(10)
                .map(u -> new UserSearchResult(u.getEmail(), u.getName(), u.getPhotoUrl()))
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
}