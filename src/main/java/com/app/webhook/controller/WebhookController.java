package com.app.webhook.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.app.webhook.dto.CreateWebhookRequest;
import com.app.webhook.entity.Webhook;
import com.app.webhook.service.WebhookService;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService service;

    public WebhookController(WebhookService service) {
        this.service = service;
    }

    @PostMapping
    public Webhook create(@RequestBody CreateWebhookRequest request,
                          Authentication auth) {

        return service.create(request, auth.getName());
    }
}