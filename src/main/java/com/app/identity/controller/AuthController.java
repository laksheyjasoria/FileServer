package com.app.identity.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.response.ApiResponse;
import com.app.identity.dto.ChangePasswordRequest;
import com.app.identity.dto.GoogleSignInRequest;
import com.app.identity.dto.LoginRequest;
import com.app.identity.dto.ProfileUpdateRequest;
import com.app.identity.dto.RegisterRequest;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;
import com.app.identity.service.PasswordService;
import com.app.orchestrator.AuthOrchestrator;
import com.app.storage.factory.StorageFactory;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthOrchestrator orchestrator;
    private final PasswordService passwordService;
    private final UserRepository userRepository;
    private final StorageFactory storageFactory; // 👈 NEW

    public AuthController(AuthOrchestrator orchestrator, PasswordService passwordService,
            UserRepository userRepository, StorageFactory storageFactory) {
        this.orchestrator = orchestrator;
        this.passwordService = passwordService;
        this.userRepository = userRepository;
        this.storageFactory = storageFactory;
    }

    // 👇 UPDATED: Accept FormData (MultipartFile) for photo during signup
    @PostMapping("/register")
    public ApiResponse<String> register(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam(required = false) String name,
            @RequestParam(value = "file", required = false) MultipartFile file) throws java.io.IOException {
        // Delegates to orchestrator
        return ApiResponse.success(orchestrator.register(email, password, name, file));
    }

    // 👇 If you want to support both JSON and FormData, you can overload:
    @PostMapping("/register/json")
    public ApiResponse<String> registerJson(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.success(orchestrator.register(req.getEmail(), req.getPassword(), req.getName()));
    }

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest req) {
        return ApiResponse.success(orchestrator.login(req.getEmail(), req.getPassword()));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgot(@RequestParam String email) {
        passwordService.sendResetLink(email);
        return ApiResponse.success("Reset email sent");
    }

    @PostMapping("/reset-password")
    public ApiResponse<String> reset(@RequestParam String token, @RequestParam String password) {
        passwordService.resetPassword(token, password);
        return ApiResponse.success("Password updated");
    }

    @GetMapping("/me")
    public ApiResponse<User> me(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return ApiResponse.error("Unauthenticated");
        }
        return userRepository.findByEmail(auth.getName())
                .map(ApiResponse::success)
                .orElse(ApiResponse.error("User not found"));
    }

    @PostMapping("/google")
    public ApiResponse<String> google(@RequestBody GoogleSignInRequest req) {
        return ApiResponse.success(orchestrator.google(req.getIdToken()));
    }

    @PutMapping("/profile")
    public ApiResponse<User> profile(@RequestBody ProfileUpdateRequest req, Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ApiResponse.error("Unauthenticated");
        return ApiResponse.success(orchestrator.updateProfile(auth.getName(), req.getName(), req.getPhotoUrl()));
    }

    @PostMapping("/change-password")
    public ApiResponse<String> changePassword(@RequestBody ChangePasswordRequest req, Authentication auth) {
        if (auth == null || auth.getName() == null)
            return ApiResponse.error("Unauthenticated");
        orchestrator.changePassword(auth.getName(), req.getOldPassword(), req.getNewPassword());
        return ApiResponse.success("Password updated");
    }

    // 👇 UPDATED: Calls Orchestrator to upload with unique name
    @PostMapping("/profile/photo")
    public ApiResponse<String> profilePhoto(@RequestParam("file") MultipartFile file, Authentication auth)
            throws java.io.IOException {
        if (auth == null || auth.getName() == null) {
            return ApiResponse.error("Unauthenticated");
        }
        return ApiResponse.success(orchestrator.uploadProfilePhoto(auth.getName(), file));
    }

    // 👇 NEW: Fetch profile photo securely via JWT
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

        // 1. Download bytes from Telegram
        byte[] content = storageFactory.get().download(fileId);

        // 2. Determine MediaType based on filename (stored in fileId)
        MediaType mediaType = getMediaTypeForFileName(fileId);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(content);
    }

    // ================= HELPER METHODS =================

    private String generateUniqueFileName(String userId, String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        } else {
            extension = ".jpg";
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomId = UUID.randomUUID().toString().substring(0, 8);
        return userId + "_" + timestamp + "_" + randomId + extension;
    }

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