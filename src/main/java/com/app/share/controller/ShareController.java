package com.app.share.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.app.share.dto.CreateShareRequest;
import com.app.share.dto.ShareResponse;
import com.app.share.entity.SharedResource;
import com.app.share.service.ShareService;
import com.app.share.dto.PublicShareResponse;
import com.app.master.entity.MasterFile;
import com.app.storage.factory.StorageFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;

@RestController
@RequestMapping("/share")
public class ShareController {

    private final ShareService service;
    private final StorageFactory storageFactory;

    public ShareController(ShareService service, StorageFactory storageFactory) {
        this.service = service;
        this.storageFactory = storageFactory;
    }

    @PostMapping
    public ShareResponse create(@RequestBody CreateShareRequest request,
                                Authentication auth) {

        return service.create(request, auth.getName());
    }

    @GetMapping("/{token}")
    public PublicShareResponse access(@PathVariable String token,
                                 @RequestParam(required = false)
                                 String password) {

        return service.details(token, password);
    }

    @GetMapping("/stream/{token}")
    public ResponseEntity<byte[]> stream(@PathVariable String token,
                                          @RequestParam(required = false) String password) {
        MasterFile file = service.file(token, password);
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try { if (file.getContentType() != null) type = MediaType.parseMediaType(file.getContentType()); }
        catch (IllegalArgumentException ignored) { }
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.getName()).build().toString())
                .body(storageFactory.get().download(file.getFileId()));
    }
}
