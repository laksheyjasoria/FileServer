package com.app.upload.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.app.upload.service.UploadService;

@RestController
@RequestMapping("/upload")
public class UploadController {

    private final UploadService service;

    public UploadController(UploadService service) {
        this.service = service;
    }

    @PostMapping
    public String upload(@RequestParam MultipartFile file,
                         @RequestParam(required = false) String parentId,
                         Authentication auth) {

        return service.upload(file, auth.getName(), parentId);
    }
}
