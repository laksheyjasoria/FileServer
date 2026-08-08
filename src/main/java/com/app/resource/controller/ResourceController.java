package com.app.resource.controller;

import org.springframework.web.bind.annotation.*;

import com.app.resource.dto.ResourceActionRequest;
import com.app.resource.service.ResourceService;

@RestController
@RequestMapping("/resources")
public class ResourceController {

    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    @PostMapping("/action")
    public void action(@RequestBody ResourceActionRequest request,
            org.springframework.security.core.Authentication auth) {
        String userId = auth != null ? auth.getName() : null;
        service.handle(request, userId);
    }
}