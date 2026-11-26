package com.app.share.controller;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
import com.app.transfer.ByteRange;
import com.app.transfer.FileTransferService;
import com.app.transfer.HttpRangeParser;
import com.app.transfer.RangeRequest;

@RestController
@RequestMapping("/share")
public class ShareController {

    private final ShareService service;
    private final MasterFileRepository files;
    private final FileTransferService fileTransferService;
    private final HttpRangeParser rangeParser;

    public ShareController(
            ShareService service,
            MasterFileRepository files,
            FileTransferService fileTransferService,
            HttpRangeParser rangeParser) {

        this.service = service;
        this.files = files;
        this.fileTransferService = fileTransferService;
        this.rangeParser = rangeParser;
    }

    @PostMapping
    public ShareResponse create(
            @RequestBody CreateShareRequest request,
            Authentication auth) {
        return service.create(request, auth.getName());
    }

    @PostMapping("/multi")
    public ShareResponse createMulti(
            @RequestBody CreateMultiShareRequest request,
            Authentication auth) {
        return service.createMulti(request, auth.getName());
    }

    @GetMapping("/shared-by-me")
    public ResponseEntity<List<PublicShareResponse>> getSharedByMe(
            Authentication auth) {

        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(service.getSharedByMe(auth.getName()));
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<List<PublicShareResponse>> getSharedWithMe(
            Authentication auth) {

        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(service.getSharedWithMe(auth.getName()));
    }

    @DeleteMapping("/{token}")
    public ResponseEntity<?> deleteShare(
            @PathVariable String token,
            Authentication auth) {

        if (auth == null || auth.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        service.deleteShare(token, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{token}")
    public PublicShareResponse access(
            @PathVariable String token,
            @RequestParam(required = false) String password) {
        return service.details(token, password);
    }

    /**
     * Public share stream.
     *
     * Supports HTTP Range so a shared video/audio/PDF can seek without first
     * downloading the complete file.
     */
    @GetMapping("/stream/{token}")
    public ResponseEntity<?> stream(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String fileId,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {

        SharedResource share = service.validate(token, password);
        MasterFile file = resolveSharedFile(token, password, fileId);

        SharePermission permission = resolvePermission(share);

        if (permission == SharePermission.DOWNLOAD) {
            throw new ShareAccessDeniedException(
                    "You do not have permission to view this file.");
        }

        validateStoredFile(file);

        return streamFile(file, false, rangeHeader);
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<?> download(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String fileId) {

        SharedResource share = service.validate(token, password);
        MasterFile file = resolveSharedFile(token, password, fileId);

        SharePermission permission = resolvePermission(share);

        if (permission == SharePermission.VIEW) {
            throw new ShareAccessDeniedException(
                    "You do not have permission to download this file.");
        }

        validateStoredFile(file);

        return streamFile(file, true, null);
    }

    @GetMapping("/{token}/contents")
    public List<MasterFile> getFolderContents(
            @PathVariable String token,
            @RequestParam(required = false) String password) {
        return service.folderContents(token, password);
    }

    @GetMapping("/{token}/folder/{folderId}/contents")
    public List<MasterFile> getSubfolderContents(
            @PathVariable String token,
            @PathVariable String folderId,
            @RequestParam(required = false) String password) {
        return service.folderContents(token, password, folderId);
    }

    @GetMapping("/{token}/items")
    public List<MasterFile> getSharedItems(
            @PathVariable String token,
            @RequestParam(required = false) String password) {

        SharedResource share = service.validate(token, password);

        if (share.getFileIds() == null || share.getFileIds().isEmpty()) {
            throw new IllegalStateException("No items in this share.");
        }

        return files.findAllById(share.getFileIds());
    }

    private MasterFile resolveSharedFile(
            String token,
            String password,
            String fileId) {

        if (fileId != null && !fileId.isBlank()) {
            service.validateFileInShare(token, password, fileId);

            return files.findById(fileId)
                    .orElseThrow(com.app.core.exception.FileNotFoundException::new);
        }

        return service.file(token, password);
    }

    private SharePermission resolvePermission(SharedResource share) {
        if (share.getPermission() == null) {
            return SharePermission.VIEW_DOWNLOAD;
        }

        return share.getPermission();
    }

    private void validateStoredFile(MasterFile file) {

        if (file == null) {
            throw new com.app.core.exception.FileNotFoundException();
        }

        if (!file.isActive()) {
            throw new com.app.core.exception.FileNotFoundException();
        }

        if ("FOLDER".equalsIgnoreCase(file.getDriveType())) {
            throw new IllegalArgumentException(
                    "This endpoint is for files only, not folders.");
        }

        if (!fileTransferService.isChunked(file)
                && !fileTransferService.isLegacy(file)) {
            throw new IllegalStateException(
                    "File has no valid storage reference: " + file.getName());
        }
    }

    private ResponseEntity<?> streamFile(
            MasterFile file,
            boolean download,
            String rangeHeader) {

        long fileSize = file.getSize() == null ? 0L : file.getSize();
        MediaType type = resolveContentType(file.getContentType());

        if (fileSize == 0L) {
            HttpHeaders headers = buildHeaders(file, type, download);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            headers.setContentLength(0L);

            StreamingResponseBody body = outputStream -> {
                // Empty file.
            };

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(body);
        }

        final ByteRange range;

        try {
            if (rangeHeader == null || rangeHeader.isBlank()) {
                range = new ByteRange(0L, fileSize - 1L);
            } else {
                RangeRequest request = rangeParser.parse(rangeHeader);
                range = rangeParser.resolve(request, fileSize);
            }
        } catch (IllegalArgumentException ex) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize);
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

            return ResponseEntity.status(
                    HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .headers(headers)
                    .build();
        }

        StreamingResponseBody body = outputStream ->
                fileTransferService.streamRange(file, range, outputStream);

        HttpHeaders headers = buildHeaders(file, type, download);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setContentLength(range.getLength());

        boolean partial = rangeHeader != null && !rangeHeader.isBlank();

        if (partial) {
            headers.set(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.getStart()
                            + "-" + range.getEnd()
                            + "/" + fileSize);

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .body(body);
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }

    private HttpHeaders buildHeaders(
            MasterFile file,
            MediaType type,
            boolean download) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setCacheControl("private, no-store");
        headers.set("X-Content-Type-Options", "nosniff");

        if (download) {
            headers.setContentDisposition(
                    ContentDisposition.attachment()
                            .filename(file.getName())
                            .build());
        } else {
            headers.setContentDisposition(
                    ContentDisposition.inline()
                            .filename(file.getName())
                            .build());
        }

        return headers;
    }

    private MediaType resolveContentType(String contentType) {

        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
