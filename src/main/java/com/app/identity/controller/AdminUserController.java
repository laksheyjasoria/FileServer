package com.app.identity.controller;

import com.app.identity.entity.User;
import com.app.identity.enums.UserStatus;
import com.app.identity.service.UserManagementService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserManagementService userService;

    public AdminUserController(UserManagementService userService) {
        this.userService = userService;
    }

    @GetMapping
    public Page<User> getUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return userService.getUsers(status, search, pageable);
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<User> updateStatus(
            @PathVariable Long userId,
            @RequestBody StatusUpdateRequest request) {
        User updated = userService.updateUserStatus(userId, UserStatus.valueOf(request.getStatus().toUpperCase()));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    static class StatusUpdateRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}