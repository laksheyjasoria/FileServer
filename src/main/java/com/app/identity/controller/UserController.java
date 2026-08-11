package com.app.identity.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.identity.dto.UserSearchResult;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepo;

    public UserController(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping("/search")
    public List<UserSearchResult> searchUsers(@RequestParam("q") String query, Authentication auth) {
        // If query is blank, return empty list
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Search database for users by email or name (case insensitive)
        List<User> users = userRepo.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(query, query);

        // Convert to DTO and limit to 10 results
        return users.stream()
                .limit(10)
                .map(u -> new UserSearchResult(u.getEmail(), u.getName(), u.getPhotoUrl()))
                .collect(Collectors.toList());
    }
}