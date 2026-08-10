package com.app.share.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.app.core.exception.ShareAccessDeniedException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.share.dto.CreateMultiShareRequest;
import com.app.share.dto.CreateShareRequest;
import com.app.share.dto.PublicShareResponse;
import com.app.share.dto.ShareResponse;
import com.app.share.entity.SharedResource;
import com.app.share.entity.SharePermission;
import com.app.share.service.ShareService;
import com.app.storage.factory.StorageFactory;

@RestController
@RequestMapping("/share")
public class ShareController {

    private final ShareService service;
    private final StorageFactory storageFactory;
    private final MasterFileRepository files;

    public ShareController(ShareService service, StorageFactory storageFactory, MasterFileRepository files) {
        this.service = service;
        this.storageFactory = storageFactory;
        this.files = files;
    }

    // ----- Create single share link -----
    @PostMapping
    public ShareResponse create(@RequestBody CreateShareRequest request, Authentication auth) {
        return service.create(request, auth.getName());
    }

    // ----- Create multi‑share link -----
    @PostMapping("/multi")
    public ShareResponse createMulti(@RequestBody CreateMultiShareRequest request, Authentication auth) {
        return service.createMulti(request, auth.getName());
    }

    // ----- Get share details -----
    @GetMapping("/{token}")
    public PublicShareResponse access(@PathVariable String token,
                                      @RequestParam(required = false) String password) {
        return service.details(token, password);
    }

    // ----- Stream file (inline preview) -----
    @GetMapping("/stream/{token}")
    public ResponseEntity<byte[]> stream(@PathVariable String token,
                                         @RequestParam(required = false) String password,
                                         @RequestParam(required = false) String fileId) { // 👈 ADD fileId
        SharedResource share = service.validate(token, password);
        MasterFile file;

        if (fileId != null && !fileId.isBlank()) {
            // 🛡️ Ensure the requested file actually belongs to this share
            boolean isAuthorized = false;
            if (share.getFileIds() != null && share.getFileIds().contains(fileId)) {
                isAuthorized = true;
            } else if (share.getFileId() != null && share.getFileId().equals(fileId)) {
                isAuthorized = true;
            }
            if (!isAuthorized) {
                throw new ShareAccessDeniedException("This file is not part of the shared resource.");
            }
            file = files.findById(fileId).orElseThrow(com.app.core.exception.FileNotFoundException::new);
        } else {
            // Default: fetch the root shared file
            file = service.file(token, password);
        }

        // 🔐 Enforce Share Permission (VIEW or VIEW_DOWNLOAD)
        SharePermission perm = share.getPermission();
        if (perm == null) { perm = SharePermission.VIEW_DOWNLOAD; }
        if (perm == SharePermission.DOWNLOAD) {
            throw new ShareAccessDeniedException("You do not have permission to view this file.");
        }

        if ("FOLDER".equalsIgnoreCase(file.getDriveType())) {
            throw new RuntimeException("Stream endpoint is for files only, not folders.");
        }
        validateFileId(file);

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (file.getContentType() != null) {
                type = MediaType.parseMediaType(file.getContentType());
            }
        } catch (IllegalArgumentException ignored) {
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.getName()).build().toString())
                .body(storageFactory.get().download(file.getFileId()));
    }

    // ----- Download file (attachment) -----
    @GetMapping("/download/{token}")
    public ResponseEntity<byte[]> download(@PathVariable String token,
                                           @RequestParam(required = false) String password,
                                           @RequestParam(required = false) String fileId) { // 👈 ADD fileId
        SharedResource share = service.validate(token, password);
        MasterFile file;

        if (fileId != null && !fileId.isBlank()) {
            // 🛡️ Ensure the requested file actually belongs to this share
            boolean isAuthorized = false;
            if (share.getFileIds() != null && share.getFileIds().contains(fileId)) {
                isAuthorized = true;
            } else if (share.getFileId() != null && share.getFileId().equals(fileId)) {
                isAuthorized = true;
            }
            if (!isAuthorized) {
                throw new ShareAccessDeniedException("This file is not part of the shared resource.");
            }
            file = files.findById(fileId).orElseThrow(com.app.core.exception.FileNotFoundException::new);
        } else {
            // Default: download the root shared file
            file = service.file(token, password);
        }

        // 🔐 Enforce Share Permission (DOWNLOAD or VIEW_DOWNLOAD)
        SharePermission perm = share.getPermission();
        if (perm == null) { perm = SharePermission.VIEW_DOWNLOAD; }
        if (perm == SharePermission.VIEW) {
            throw new ShareAccessDeniedException("You do not have permission to download this file.");
        }

        if ("FOLDER".equalsIgnoreCase(file.getDriveType())) {
            throw new RuntimeException("Download endpoint is for files only, not folders.");
        }
        validateFileId(file);

        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (file.getContentType() != null) {
                type = MediaType.parseMediaType(file.getContentType());
            }
        } catch (IllegalArgumentException ignored) {
        }
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getName()).build().toString())
                .body(storageFactory.get().download(file.getFileId()));
    }

    // ----- Folder contents (single folder) -----
    @GetMapping("/{token}/contents")
    public List<MasterFile> getFolderContents(@PathVariable String token,
                                              @RequestParam(required = false) String password) {
        return service.folderContents(token, password);
    }

    // ----- Subfolder contents (for navigation) -----
    @GetMapping("/{token}/folder/{folderId}/contents")
    public List<MasterFile> getSubfolderContents(@PathVariable String token,
                                                 @PathVariable String folderId,
                                                 @RequestParam(required = false) String password) {
        return service.folderContents(token, password, folderId);
    }

    // ----- Multi‑share items -----
    @GetMapping("/{token}/items")
    public List<MasterFile> getSharedItems(@PathVariable String token,
                                           @RequestParam(required = false) String password) {
        SharedResource share = service.validate(token, password);
        if (share.getFileIds() == null || share.getFileIds().isEmpty()) {
            throw new RuntimeException("No items in this share");
        }
        return files.findAllById(share.getFileIds());
    }

    // 👇 Helper validation method
    private void validateFileId(MasterFile file) {
        if (file.getFileId() == null || file.getFileId().isBlank()) {
            throw new RuntimeException("File ID missing for file: " + file.getName());
        }
    }
}