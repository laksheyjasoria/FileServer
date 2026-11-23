package com.app.drive.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.app.core.exception.FileNotFoundException;
import com.app.master.entity.MasterFile;
import com.app.master.repository.MasterFileRepository;
import com.app.orchestrator.UploadOrchestrator;
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;
import com.app.upload.dto.ChunkUploadResponse;
import com.app.upload.dto.CreateUploadRequest;
import com.app.upload.entity.UploadJob;
import com.app.upload.service.ChunkService;
import com.app.upload.service.ChunkUploadStateService;
import com.app.drive.service.MasterDriveAsyncUploadService;
import com.app.upload.service.PauseService;
import com.app.upload.service.ResumeService;
import com.app.upload.service.CancelService;
import com.app.upload.service.UploadStatusService;

/**
 * Central Master Drive API. Physical file bytes remain in the configured storage
 * provider; this controller only manages MasterFile metadata and delegates
 * chunk transfer/streaming to the existing services.
 */
@RestController
@RequestMapping("/master-drive")
public class MasterDriveController {

    private final MasterFileRepository files;
    private final UploadOrchestrator uploadOrchestrator;
    private final ChunkService chunkService;
    private final ChunkUploadStateService chunkStateService;
    private final MasterDriveAsyncUploadService asyncUploadService;
    private final ResumeService resumeService;
    private final PauseService pauseService;
    private final CancelService cancelService;
    private final UploadStatusService statusService;
    private final FileTransferService transferService;
    private final HttpRangeParser rangeParser;

    @Value("${app.master-drive.stream-token.expiration-seconds:3600}")
    private long streamTokenExpirationSeconds;

    private final com.app.core.security.jwt.JwtService jwtService;

    public MasterDriveController(
            MasterFileRepository files,
            UploadOrchestrator uploadOrchestrator,
            ChunkService chunkService,
            ChunkUploadStateService chunkStateService,
            MasterDriveAsyncUploadService asyncUploadService,
            ResumeService resumeService,
            PauseService pauseService,
            CancelService cancelService,
            UploadStatusService statusService,
            FileTransferService transferService,
            HttpRangeParser rangeParser,
            com.app.core.security.jwt.JwtService jwtService) {
        this.files = files;
        this.uploadOrchestrator = uploadOrchestrator;
        this.chunkService = chunkService;
        this.chunkStateService = chunkStateService;
        this.asyncUploadService = asyncUploadService;
        this.resumeService = resumeService;
        this.pauseService = pauseService;
        this.cancelService = cancelService;
        this.statusService = statusService;
        this.transferService = transferService;
        this.rangeParser = rangeParser;
        this.jwtService = jwtService;
    }

    @GetMapping
    public List<MasterFile> listDrives() {
        return rootDrives();
    }

    @GetMapping("/{driveId}")
    public MasterFile getDrive(@PathVariable String driveId) {
        return requireDrive(driveId);
    }

    @GetMapping("/name/{driveName}")
    public MasterFile getDriveByName(@PathVariable String driveName) {
        return rootDrives().stream()
                .filter(d -> d.getName() != null && d.getName().equalsIgnoreCase(driveName))
                .findFirst()
                .orElseThrow(() -> new FileNotFoundException("Master Drive not found"));
    }

    @GetMapping("/{driveId}/contents")
    public List<MasterFile> contents(@PathVariable String driveId,
            @RequestParam(required = false) String folderId) {
        MasterFile drive = requireDrive(driveId);
        String parentId = folderId == null || folderId.isBlank() ? drive.getId() : folderId;
        MasterFile folder = requireFolderInDrive(drive, parentId);
        return files.findByParentIdAndActiveTrue(folder.getId()).stream()
                .filter(item -> "FOLDER".equalsIgnoreCase(item.getDriveType())
                        || ("FILE".equalsIgnoreCase(item.getDriveType())
                                && item.getUploadJobId() != null && !item.getUploadJobId().isBlank()))
                .toList();
    }

    @PostMapping("/{driveId}/folders")
    public ResponseEntity<MasterFile> createFolder(@PathVariable String driveId,
            @RequestBody CreateFolderRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Folder name is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Upload request cannot be null.");
        }
        MasterFile drive = requireDrive(driveId);
        String parentId = request.getParentId() == null || request.getParentId().isBlank()
                ? drive.getId() : request.getParentId();
        requireFolderInDrive(drive, parentId);

        MasterFile folder = new MasterFile();
        folder.setName(request.getName().trim());
        folder.setDriveType("FOLDER");
        folder.setUserId(drive.getUserId());
        folder.setParentId(parentId);
        folder.setSize(0L);
        folder.setAccessType("PUBLIC");
        return ResponseEntity.status(HttpStatus.CREATED).body(files.save(folder));
    }


    /**
     * Accepts one multipart file, transfers ownership of its temporary file to the
     * background worker, and returns an upload ID while Telegram chunk transfer continues.
     * The temporary staging file is deleted by the worker after completion/failure.
     */
    @PostMapping(value = "/{driveId}/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(@PathVariable String driveId,
            @RequestParam("file") MultipartFile file, Authentication auth) {
        if (file == null) {
            throw new IllegalArgumentException("File is required.");
        }

        MasterFile drive = requireDrive(driveId);
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("File name is required.");
        }

        CreateUploadRequest request = new CreateUploadRequest();
        request.setFileName(fileName);
        request.setTotalSize(file.getSize());
        request.setContentType(file.getContentType());
        request.setParentId(drive.getId());

        UploadJob job = uploadOrchestrator.createForMasterDrive(
                request, auth.getName(), drive.getUserId());

        if (job.getStatus() == com.app.upload.entity.UploadStatus.COMPLETED) {
            return ResponseEntity.ok(statusService.getStatus(job.getId(), auth.getName()));
        }

        Path stagedFile = null;
        try {
            Path stagingDirectory = Path.of(
                    System.getProperty("java.io.tmpdir"), "fileserver-master-drive-uploads");
            Files.createDirectories(stagingDirectory);
            // Keep the servlet multipart temp file on the same filesystem as
            // this staging directory. StandardServletMultipartFile.transferTo()
            // delegates to Part.write(), which can move/rename the already
            // received multipart temp file instead of copying all bytes again.
            // The worker now owns this path after the request returns.
            stagedFile = stagingDirectory.resolve(
                    "master-drive-" + job.getId() + "-" + UUID.randomUUID() + ".upload");
            file.transferTo(stagedFile.toFile());

            asyncUploadService.processUpload(
                    job.getId(),
                    auth.getName(),
                    fileName,
                    file.getContentType(),
                    job.getTotalSize(),
                    job.getChunkSize(),
                    stagedFile);

            return ResponseEntity.accepted().body(Map.of(
                    "success", true,
                    "uploadId", job.getId(),
                    "fileName", fileName,
                    "totalSize", job.getTotalSize(),
                    "status", "IN_PROGRESS",
                    "message", "Upload accepted. Check the upload status using the uploadId."));
        } catch (IOException | RuntimeException ex) {
            if (stagedFile != null) {
                try {
                    Files.deleteIfExists(stagedFile);
                } catch (IOException cleanupError) {
                    ex.addSuppressed(cleanupError);
                }
            }
            try {
                chunkStateService.markUploadFailed(job.getId(), auth.getName());
            } catch (RuntimeException stateError) {
                ex.addSuppressed(stateError);
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "success", false,
                    "uploadId", job.getId(),
                    "fileName", fileName,
                    "status", "FAILED",
                    "message", ex.getMessage() == null ? "Could not start file upload." : ex.getMessage()));
        }
    }

    @PostMapping("/{driveId}/upload/create")
    public UploadJob createUpload(@PathVariable String driveId,
            @RequestBody CreateUploadRequest request, Authentication auth) {
        MasterFile drive = requireDrive(driveId);
        String parentId = request.getParentId() == null || request.getParentId().isBlank()
                ? drive.getId() : request.getParentId();
        requireFolderInDrive(drive, parentId);
        request.setParentId(parentId);
        return uploadOrchestrator.createForMasterDrive(request, auth.getName(), drive.getUserId());
    }

    @PostMapping("/upload/{uploadId}/{chunkIndex}")
    public ResponseEntity<Void> uploadChunk(@PathVariable String uploadId,
            @PathVariable Integer chunkIndex, @RequestParam("file") MultipartFile file,
            Authentication auth) {
        chunkService.uploadChunk(uploadId, chunkIndex, file, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/upload/{uploadId}/status", "/uploads/{uploadId}/status"})
    public ChunkUploadResponse uploadStatus(@PathVariable String uploadId, Authentication auth) {
        return statusService.getStatus(uploadId, auth.getName());
    }

    @GetMapping("/upload/{uploadId}/resume")
    public List<Integer> resumeUpload(@PathVariable String uploadId, Authentication auth) {
        return resumeService.resume(uploadId, auth.getName());
    }

    @PostMapping("/upload/{uploadId}/pause")
    public ResponseEntity<Void> pauseUpload(@PathVariable String uploadId, Authentication auth) {
        pauseService.pause(uploadId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/upload/{uploadId}")
    public ResponseEntity<Void> cancelUpload(@PathVariable String uploadId, Authentication auth) {
        cancelService.cancel(uploadId, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/files/{fileId}/metadata")
    public MasterFile metadata(@PathVariable String fileId) {
        return requireFile(fileId);
    }

    /**
     * Creates a short-lived, file-scoped URL that can be opened directly by a browser
     * media element, which cannot attach the Admin JWT or Master Key headers.
     */
    @GetMapping("/files/{fileId}/stream-url")
    public Map<String, Object> createStreamUrl(@PathVariable String fileId) {
        MasterFile file = requireFile(fileId);
        if (!transferService.isChunked(file)) {
            throw new IllegalStateException("Master Drive file has no valid chunked storage reference.");
        }
        long validitySeconds = Math.max(1L, streamTokenExpirationSeconds);
        String token = jwtService.generateFileAccessToken(file.getId(), validitySeconds);
        String streamUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/master-drive/files/{fileId}/stream")
                .queryParam("token", token)
                .buildAndExpand(file.getId())
                .toUriString();

        return Map.of(
                "success", true,
                "fileId", file.getId(),
                "streamUrl", streamUrl,
                "expiresIn", validitySeconds);
    }

    @GetMapping("/files/{fileId}/stream")
    public ResponseEntity<StreamingResponseBody> stream(@PathVariable String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        MasterFile file = requireFile(fileId);
        return transferResponse(file, rangeHeader, true);
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        MasterFile file = requireFile(fileId);
        return transferResponse(file, rangeHeader, false);
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        MasterFile file = requireFile(fileId);
        file.setActive(false);
        file.setDeletedAt(LocalDateTime.now());
        files.save(file);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable String folderId) {
        MasterFile folder = requireFolder(folderId);
        List<String> descendants = files.findAllDescendantIds(folderId);
        if (!descendants.isEmpty()) {
            files.softDeleteAllByIds(descendants);
        }
        folder.setActive(false);
        folder.setDeletedAt(LocalDateTime.now());
        files.save(folder);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<StreamingResponseBody> transferResponse(
            MasterFile file, String rangeHeader, boolean inline) {
        if (!"FILE".equalsIgnoreCase(file.getDriveType())) {
            throw new IllegalArgumentException("Only files can be transferred.");
        }
        long size = file.getSize() == null ? -1L : file.getSize();
        if (size < 0 || !transferService.isChunked(file)) {
            throw new IllegalStateException("Master Drive file must have a valid chunked storage reference.");
        }
        MediaType type = resolveContentType(file.getContentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentDisposition((inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getName()).build());
        if (size == 0) {
            headers.setContentLength(0);
            return ResponseEntity.ok().headers(headers).body(output -> { });
        }

        boolean partial = rangeHeader != null && !rangeHeader.isBlank();
        ByteRange range;
        try {
            range = partial
                    ? rangeParser.resolve(rangeParser.parse(rangeHeader), size)
                    : new ByteRange(0L, size - 1L);
        } catch (IllegalArgumentException ex) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + size);
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .headers(headers).build();
        }
        headers.setContentLength(range.getLength());
        StreamingResponseBody body = output -> transferService.streamRange(file, range, output);
        if (partial) {
            headers.set(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.getStart() + "-" + range.getEnd() + "/" + size);
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(body);
        }
        return ResponseEntity.ok().headers(headers).body(body);
    }

    private List<MasterFile> rootDrives() {
        return files.findByDriveTypeAndUserIdInAndParentIdIsNull("FOLDER", java.util.Arrays.asList(null, "admin"))
                .stream().filter(MasterFile::isActive).toList();
    }

    private MasterFile requireDrive(String id) {
        MasterFile drive = files.findById(id)
                .filter(MasterFile::isActive)
                .filter(f -> "FOLDER".equalsIgnoreCase(f.getDriveType()) && f.getParentId() == null)
                .orElseThrow(() -> new FileNotFoundException("Master Drive not found"));
        if (!java.util.Arrays.asList(null, "admin").contains(drive.getUserId())) {
            throw new FileNotFoundException("Master Drive not found");
        }
        return drive;
    }

    private MasterFile requireFolderInDrive(MasterFile drive, String folderId) {
        MasterFile folder = requireFolder(folderId);
        if (!isWithinDrive(folder, drive.getId())) {
            throw new FileNotFoundException("Folder does not belong to the requested Master Drive");
        }
        return folder;
    }

    private MasterFile requireFolder(String id) {
        return files.findByIdAndActiveTrue(id)
                .filter(f -> "FOLDER".equalsIgnoreCase(f.getDriveType()))
                .orElseThrow(() -> new FileNotFoundException("Folder not found"));
    }

    private MasterFile requireFile(String id) {
        MasterFile file = files.findByIdAndActiveTrue(id)
                .filter(f -> "FILE".equalsIgnoreCase(f.getDriveType()))
                .orElseThrow(() -> new FileNotFoundException("File not found"));
        if (rootFor(file) == null) {
            throw new FileNotFoundException("File is not part of a Master Drive");
        }
        return file;
    }

    private MasterFile rootFor(MasterFile item) {
        String parentId = item.getParentId();
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (parentId != null && visited.add(parentId)) {
            MasterFile parent = files.findById(parentId).orElse(null);
            if (parent == null) return null;
            if (parent.getParentId() == null && "FOLDER".equalsIgnoreCase(parent.getDriveType())) {
                return java.util.Arrays.asList(null, "admin").contains(parent.getUserId()) ? parent : null;
            }
            parentId = parent.getParentId();
        }
        return null;
    }

    private boolean isWithinDrive(MasterFile item, String driveId) {
        if (item.getId().equals(driveId)) return true;
        return rootFor(item) != null && rootFor(item).getId().equals(driveId);
    }

    private MediaType resolveContentType(String value) {
        if (value == null || value.isBlank()) return MediaType.APPLICATION_OCTET_STREAM;
        try { return MediaType.parseMediaType(value); }
        catch (IllegalArgumentException ex) { return MediaType.APPLICATION_OCTET_STREAM; }
    }


    public static class CreateFolderRequest {
        private String name;
        private String parentId;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getParentId() { return parentId; }
        public void setParentId(String parentId) { this.parentId = parentId; }
    }
}
