package com.app.identity.controller;

import com.app.identity.entity.User;
import com.app.identity.enums.IncomingSharePrivacy;
import com.app.identity.enums.PrivacyLevel;
import com.app.identity.repository.UserRepository;
import com.app.identity.service.UserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me/privacy")
public class PrivacyController {

    private final UserManagementService userService;
    private final UserRepository userRepository;

    public PrivacyController(UserManagementService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    // ================================
    // INCOMING SHARE PRIVACY
    // ================================

    @GetMapping("/incoming-share")
    public ResponseEntity<IncomingSharePrivacy> getIncomingSharePrivacy(Authentication auth) {
        User user = userService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(user.getIncomingSharePrivacy());
    }

    @PutMapping("/incoming-share")
    public ResponseEntity<Void> updateIncomingSharePrivacy(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        User user = userService.getCurrentUser(auth.getName());
        user.setIncomingSharePrivacy(IncomingSharePrivacy.valueOf(request.get("privacy")));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    // ================================
    // FRIEND REQUEST PRIVACY
    // ================================

    @GetMapping("/friend-requests")
    public ResponseEntity<PrivacyLevel> getFriendRequestPrivacy(Authentication auth) {
        User user = userService.getCurrentUser(auth.getName());
        return ResponseEntity.ok(user.getFriendRequestPrivacy());
    }

    @PutMapping("/friend-requests")
    public ResponseEntity<Void> updateFriendRequestPrivacy(
            @RequestBody Map<String, String> request,
            Authentication auth) {
        User user = userService.getCurrentUser(auth.getName());
        user.setFriendRequestPrivacy(PrivacyLevel.valueOf(request.get("friendRequestPrivacy")));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    // ================================
    // AUTO-APPROVE FRIENDS
    // ================================

    @PutMapping("/auto-approve-friends")
    public ResponseEntity<Void> setAutoApproveFriends(@RequestParam boolean enabled, Authentication auth) {
        User user = userService.getCurrentUser(auth.getName());
        user.setAutoApproveFriends(enabled);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}