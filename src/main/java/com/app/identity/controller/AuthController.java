package com.app.identity.controller;

import com.app.core.response.ApiResponse;
import com.app.identity.dto.*;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;
import com.app.identity.service.PasswordService;
import com.app.orchestrator.AuthOrchestrator;
import com.app.storage.factory.StorageFactory;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthOrchestrator orchestrator;
    private final PasswordService passwordService;
    private final UserRepository userRepository;
    private final StorageFactory storageFactory;

    public AuthController(AuthOrchestrator orchestrator, PasswordService passwordService,
                          UserRepository userRepository, StorageFactory storageFactory) {
        this.orchestrator = orchestrator;
        this.passwordService = passwordService;
        this.userRepository = userRepository;
        this.storageFactory = storageFactory;
    }

    // ===== REGISTER (multipart) =====
    @PostMapping("/register")
    public ApiResponse<String> register(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String name,
            @RequestParam(value = "file", required = false) MultipartFile file) throws java.io.IOException {
        return ApiResponse.success(orchestrator.register(email, password, name, file));
    }

    @PostMapping("/register/json")
    public ApiResponse<String> registerJson(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.success(orchestrator.register(req.getEmail(), req.getPassword(), req.getName()));
    }

    // ===== LOGIN =====
    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest req) {
        return ApiResponse.success(orchestrator.login(req.getEmail(), req.getPassword()));
    }

    // ===== FORGOT PASSWORD =====
    @PostMapping("/forgot-password")
    public ApiResponse<String> forgot(@RequestParam String email) {
        passwordService.sendResetLink(email);
        return ApiResponse.success("Reset email sent");
    }

    // ===== RESET PASSWORD =====
    @PostMapping("/reset-password")
    public ApiResponse<String> reset(@RequestParam String token, @RequestParam String password) {
        passwordService.resetPassword(token, password);
        return ApiResponse.success("Password updated");
    }

    // ===== GET USER INFO (with provider) =====
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ApiResponse.error("Unauthenticated");
        }
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("name", user.getName());
        response.put("photoUrl", user.getPhotoUrl());
        response.put("provider", user.getProvider().name()); // "GOOGLE" or "LOCAL"

        return ApiResponse.success(response);
    }

    // ===== GOOGLE LOGIN (with sync flag) =====
    @PostMapping("/google")
    public ApiResponse<String> google(
            @RequestBody GoogleSignInRequest req,
            @RequestParam(defaultValue = "false") boolean sync) {
        return ApiResponse.success(orchestrator.google(req.getIdToken(), sync));
    }

    // ===== UPDATE PROFILE =====
    @PutMapping("/profile")
    public ApiResponse<User> profile(@RequestBody ProfileUpdateRequest req, Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ApiResponse.error("Unauthenticated");
        return ApiResponse.success(orchestrator.updateProfile(auth.getName(), req.getName(), req.getPhotoUrl()));
    }

    // ===== CHANGE PASSWORD =====
    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(@RequestBody ChangePasswordRequest req, Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ApiResponse.error("Unauthenticated");
        orchestrator.changePassword(auth.getName(), req.getOldPassword(), req.getNewPassword());
        return ApiResponse.success("Password updated");
    }

    // ===== UPLOAD PROFILE PHOTO =====
    @PostMapping("/profile/photo")
    public ApiResponse<String> profilePhoto(@RequestParam("file") MultipartFile file, Authentication auth)
            throws java.io.IOException {
        if (auth == null || auth.getName() == null) {
            return ApiResponse.error("Unauthenticated");
        }
        return ApiResponse.success(orchestrator.uploadProfilePhoto(auth.getName(), file));
    }

    // ===== GET PROFILE PHOTO (stream from Telegram) =====
    @GetMapping("/profile/photo")
    public ResponseEntity<byte[]> getProfilePhoto(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String fileId = user.getPhotoUrl();
        if (fileId == null || fileId.isBlank()) {
            return ResponseEntity.notFound().build();
        }

        byte[] content = storageFactory.get().download(fileId);
        MediaType mediaType = getMediaTypeForFileName(fileId);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(content);
    }

    // ================= HELPER =================
    private MediaType getMediaTypeForFileName(String filename) {
        int lastDot = filename.lastIndexOf(".");
        if (lastDot == -1) return MediaType.APPLICATION_OCTET_STREAM;
        String ext = filename.substring(lastDot + 1).toLowerCase();
        switch (ext) {
            case "png": return MediaType.IMAGE_PNG;
            case "gif": return MediaType.IMAGE_GIF;
            case "jpeg": case "jpg": return MediaType.IMAGE_JPEG;
            default: return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}