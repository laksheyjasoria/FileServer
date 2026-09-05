package com.app.file.controller;

import com.app.core.security.jwt.JwtService;
import com.app.storage.factory.StorageFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final JwtService jwtService;
    private final StorageFactory storageFactory;

    @Value("${app.file.token.validity-seconds:2592000}")
    private long defaultTokenValidity;

    public FileController(JwtService jwtService, StorageFactory storageFactory) {
        this.jwtService = jwtService;
        this.storageFactory = storageFactory;
    }

    /**
     * Generate a signed URL for a file.
     * By default, the token validity is linked to the user's current session expiry
     * (i.e., the main JWT's remaining lifetime).
     * You can override with &validity=SECONDS or &infinite=true.
     *
     * @param fileId      the file identifier
     * @param validity    optional custom validity in seconds
     * @param infinite    if true, generates a 10‑year token (overrides other settings)
     * @param auth        the authenticated user
     * @param request     the HTTP request (to read the JWT header)
     * @return JSON with the signed URL
     */
    @GetMapping("/{fileId}/signed")
    public ResponseEntity<Map<String, String>> getSignedUrl(
            @PathVariable String fileId,
            @RequestParam(required = false) Long validity,
            @RequestParam(required = false, defaultValue = "false") boolean infinite,
            Authentication auth,
            HttpServletRequest request) {

        String token;
        if (infinite) {
            token = jwtService.generateFileAccessTokenInfinite(fileId);
        } else if (validity != null && validity > 0) {
            token = jwtService.generateFileAccessToken(fileId, validity);
        } else {
            // Default: use the session's remaining validity
            Date sessionExpiry = getSessionExpiry(request);
            if (sessionExpiry != null) {
                token = jwtService.generateFileAccessToken(fileId, sessionExpiry);
                log.info("Generated file token tied to session expiry: {}", sessionExpiry);
            } else {
                // Fallback to configured default (e.g., 30 days)
                token = jwtService.generateFileAccessToken(fileId);
                log.info("Generated file token with default validity (no session found)");
            }
        }

        String signedUrl = "/api/files/stream/" + fileId + "?token=" + token;
        return ResponseEntity.ok(Map.of("url", signedUrl));
    }

    /**
     * Generate a signed URL that is always tied to the current session expiry.
     * This is a convenient shorthand for &useSession=true.
     */
    @GetMapping("/{fileId}/signed/session")
    public ResponseEntity<Map<String, String>> getSignedUrlSession(
            @PathVariable String fileId,
            Authentication auth,
            HttpServletRequest request) {

        Date sessionExpiry = getSessionExpiry(request);
        if (sessionExpiry == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = jwtService.generateFileAccessToken(fileId, sessionExpiry);
        String signedUrl = "/api/files/stream/" + fileId + "?token=" + token;
        return ResponseEntity.ok(Map.of("url", signedUrl));
    }

    // ------------------------------------------------------------
    //  Stream endpoint (unchanged, but shown for completeness)
    // ------------------------------------------------------------
    @GetMapping("/stream/{fileId}")
    public ResponseEntity<byte[]> streamFile(
            @PathVariable String fileId,
            @RequestParam String token,
            @RequestParam(required = false, defaultValue = "false") boolean download) {

        // Validate token (expiration checked automatically)
        String extractedFileId;
        try {
            extractedFileId = jwtService.validateFileAccessToken(token);
        } catch (Exception e) {
            log.warn("Invalid file token for {}", fileId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!extractedFileId.equals(fileId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Download file
        byte[] content;
        try {
            content = storageFactory.get().download(fileId);
        } catch (Exception e) {
            log.error("Download error for {}: {}", fileId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (content == null || content.length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = detectMediaType(fileId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setCacheControl("public, max-age=86400");
        if (download) {
            headers.setContentDispositionFormData("attachment", extractFilename(fileId));
        } else {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline");
        }

        return ResponseEntity.ok().headers(headers).body(content);
    }

    // ------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------
    private Date getSessionExpiry(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                return jwtService.extractExpiration(token);
            } catch (Exception e) {
                log.warn("Could not extract expiration from session token", e);
            }
        }
        return null;
    }

    private MediaType detectMediaType(String fileId) {
        String lower = fileId.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".mp4")) return MediaType.valueOf("video/mp4");
        if (lower.endsWith(".webm")) return MediaType.valueOf("video/webm");
        if (lower.endsWith(".mp3")) return MediaType.valueOf("audio/mpeg");
        if (lower.endsWith(".wav")) return MediaType.valueOf("audio/wav");
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        // fallback
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String extractFilename(String fileId) {
        if (fileId.contains(".")) return fileId;
        return "file.bin";
    }
}