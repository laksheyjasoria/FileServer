package com.app.drive.controller;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.app.drive.service.DownloadService;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.share.entity.SharedResource;
import com.app.share.repository.SharedResourceRepository;
import com.app.storage.factory.StorageFactory;

@RestController
@RequestMapping("/download")
public class DownloadController {

    private static final Logger logger = LoggerFactory.getLogger(DownloadController.class);

    private final DownloadService service;
    private final MasterFileRepository repository;
    private final StorageFactory storageFactory;
    private final SharedResourceRepository shareRepository;

    public DownloadController(DownloadService service,
                              MasterFileRepository repository,
                              StorageFactory storageFactory,
                              SharedResourceRepository shareRepository) {
        this.service = service;
        this.repository = repository;
        this.storageFactory = storageFactory;
        this.shareRepository = shareRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id,
                                 Authentication auth,
                                 @RequestParam(defaultValue = "false") boolean metadata) {
        MasterFile file = repository.findByIdAndUserId(id, auth.getName())
                .orElseThrow(com.app.core.exception.FileNotFoundException::new);
        if (metadata) {
            return ResponseEntity.ok(file);
        }

        if (file.getFileId() == null || file.getFileId().isBlank()) {
            throw new RuntimeException("File ID missing for file: " + file.getName());
        }

        byte[] content = storageFactory.get().download(file.getFileId());
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (file.getContentType() != null) {
                contentType = MediaType.parseMediaType(file.getContentType());
            }
        } catch (IllegalArgumentException ignored) {
        }
        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getName()).build().toString())
                .body(content);
    }

    @PostMapping("/bulk")
    public ResponseEntity<StreamingResponseBody> downloadBulk(@RequestBody List<String> ids, Authentication auth) {
        List<ZipEntryInfo> allEntries = new ArrayList<>();
        for (String id : ids) {
            collectFilesRecursivelyAuth(id, allEntries, auth, "");
        }
        return buildZipResponseStream(allEntries);
    }

    @PostMapping("/bulk/shared")
    public ResponseEntity<StreamingResponseBody> downloadBulkShared(@RequestBody List<String> ids,
                                                                    @RequestParam String token) {
        SharedResource share = shareRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid share token"));

        List<String> validRootIds = share.getFileIds();
        if (validRootIds == null || validRootIds.isEmpty()) {
            validRootIds = Collections.singletonList(share.getFileId());
        }

        List<ZipEntryInfo> allFiles = new ArrayList<>();
        for (String id : ids) {
            if (!isUnderSharedRoot(id, validRootIds)) {
                throw new RuntimeException("Access denied: one or more items are not part of this share");
            }
            collectFilesRecursivelyShared(id, allFiles, validRootIds, "");
        }
        return buildZipResponseStream(allFiles);
    }

    private record ZipEntryInfo(MasterFile file, String relativePath) {}

    private void collectFilesRecursivelyAuth(String id,
                                             List<ZipEntryInfo> accumulator,
                                             Authentication auth,
                                             String currentPath) {
        MasterFile item = repository.findByIdAndUserId(id, auth.getName()).orElse(null);
        if (item == null) return;

        if ("FILE".equalsIgnoreCase(item.getDriveType())) {
            if (item.getFileId() != null && !item.getFileId().isBlank()) {
                accumulator.add(new ZipEntryInfo(item, currentPath + item.getName()));
            } else {
                logger.warn("Skipping file '{}' because fileId is missing", item.getName());
            }
        } else if ("FOLDER".equalsIgnoreCase(item.getDriveType())) {
            String folderPath = currentPath + item.getName() + "/";
            accumulator.add(new ZipEntryInfo(null, folderPath));
            List<MasterFile> children = repository.findByParentId(id);
            for (MasterFile child : children) {
                collectFilesRecursivelyAuth(child.getId(), accumulator, auth, folderPath);
            }
        }
    }

    private void collectFilesRecursivelyShared(String id,
                                               List<ZipEntryInfo> accumulator,
                                               List<String> validRootIds,
                                               String currentPath) {
        MasterFile item = repository.findById(id).orElse(null);
        if (item == null) return;

        if (!isUnderSharedRoot(item.getId(), validRootIds)) return;

        if ("FILE".equalsIgnoreCase(item.getDriveType())) {
            if (item.getFileId() != null && !item.getFileId().isBlank()) {
                accumulator.add(new ZipEntryInfo(item, currentPath + item.getName()));
            } else {
                logger.warn("Skipping shared file '{}' because fileId is missing", item.getName());
            }
        } else if ("FOLDER".equalsIgnoreCase(item.getDriveType())) {
            String folderPath = currentPath + item.getName() + "/";
            accumulator.add(new ZipEntryInfo(null, folderPath));
            List<MasterFile> children = repository.findByParentId(id);
            for (MasterFile child : children) {
                collectFilesRecursivelyShared(child.getId(), accumulator, validRootIds, folderPath);
            }
        }
    }

    private boolean isUnderSharedRoot(String itemId, List<String> validRootIds) {
        if (itemId == null || validRootIds == null || validRootIds.isEmpty()) return false;
        for (String rootId : validRootIds) {
            if (isUnderSharedRoot(itemId, rootId)) return true;
        }
        return false;
    }

    private boolean isUnderSharedRoot(String itemId, String sharedRootId) {
        if (itemId.equals(sharedRootId)) return true;
        String currentId = itemId;
        Set<String> visited = new HashSet<>();
        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            MasterFile current = repository.findById(currentId).orElse(null);
            if (current == null) return false;
            // 🛡️ FIX: Check if the current ID matches the root before trying to go up further
            if (currentId.equals(sharedRootId)) return true;
            String parentId = current.getParentId();
            if (parentId == null) break;
            currentId = parentId;
        }
        return false;
    }

    private ResponseEntity<StreamingResponseBody> buildZipResponseStream(List<ZipEntryInfo> entries) {
        StreamingResponseBody stream = (OutputStream outputStream) -> {
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                for (ZipEntryInfo entryInfo : entries) {
                    if (entryInfo.file() != null) {
                        byte[] content = storageFactory.get().download(entryInfo.file().getFileId());
                        ZipEntry entry = new ZipEntry(entryInfo.relativePath());
                        zos.putNextEntry(entry);
                        zos.write(content);
                        zos.closeEntry();
                    } else {
                        ZipEntry entry = new ZipEntry(entryInfo.relativePath());
                        zos.putNextEntry(entry);
                        zos.closeEntry();
                    }
                    zos.flush();
                }
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename("download.zip").build());
        return ResponseEntity.ok().headers(headers).body(stream);
    }
}