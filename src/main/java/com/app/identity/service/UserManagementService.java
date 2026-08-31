package com.app.identity.service;

import com.app.identity.entity.User;
import com.app.identity.enums.UserStatus;
import com.app.identity.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;

    public UserManagementService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Admin: list users with filters
    public Page<User> getUsers(String statusFilter, String search, Pageable pageable) {
        if (statusFilter != null && !statusFilter.isEmpty()) {
            UserStatus status = UserStatus.valueOf(statusFilter.toUpperCase());
            if (search != null && !search.isEmpty()) {
                return userRepository.findAllByStatusAndEmailOrNameContainingIgnoreCase(status, search, pageable);
            }
            return userRepository.findAllByStatus(status, pageable);
        } else {
            // No status filter
            if (search != null && !search.isEmpty()) {
                return userRepository.findAllByEmailOrNameContainingIgnoreCaseAndStatusNot(search, UserStatus.DELETED, pageable);
            }
            return userRepository.findAllByStatusNot(UserStatus.DELETED, pageable);
        }
    }

    // Admin: update user status
    public User updateUserStatus(Long userId, UserStatus newStatus) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(newStatus);
        if (newStatus == UserStatus.DEACTIVATED) {
            user.setDeactivatedAt(Instant.now());
        } else {
            user.setDeactivatedAt(null);
        }
        return userRepository.save(user);
    }

    // Admin: hard delete
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }

    // User: soft delete self
    public void softDeleteUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(Instant.now());
        userRepository.save(user);
    }

    // User: get by email
    public User getCurrentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}