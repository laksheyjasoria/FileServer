package com.app.share.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.app.share.dto.CreateShareRequest;
import com.app.share.dto.ShareResponse;
import com.app.share.entity.SharedResource;
import com.app.share.service.ShareService;

@RestController
@RequestMapping("/share")
public class ShareController {

    private final ShareService service;

    public ShareController(ShareService service) {
        this.service = service;
    }

    @PostMapping
    public ShareResponse create(@RequestBody CreateShareRequest request,
                                Authentication auth) {

        return service.create(request, auth.getName());
    }

    @GetMapping("/{token}")
    public SharedResource access(@PathVariable String token,
                                 @RequestParam(required = false)
                                 String password) {

        return service.validate(token, password);
    }
}