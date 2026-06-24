package com.app.webhook.service;

import java.util.List;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.app.core.exception.WebhookNotFoundException;
import com.app.webhook.dto.CreateWebhookRequest;
import com.app.webhook.entity.Webhook;
import com.app.webhook.entity.WebhookEvent;
import com.app.webhook.repository.WebhookEventRepository;
import com.app.webhook.repository.WebhookRepository;

@Service
public class WebhookService {

    private final WebhookRepository webhookRepo;
    private final WebhookEventRepository eventRepo;

    private final RestTemplate restTemplate = new RestTemplate();

    public WebhookService(WebhookRepository webhookRepo,
                          WebhookEventRepository eventRepo) {
        this.webhookRepo = webhookRepo;
        this.eventRepo = eventRepo;
    }

    public Webhook create(CreateWebhookRequest request,
                          String userId) {

        Webhook webhook = new Webhook();

        webhook.setUserId(userId);
        webhook.setUrl(request.getUrl());
        webhook.setSecret(request.getSecret());

        return webhookRepo.save(webhook);
    }

    public void trigger(String webhookId,
                        String payload) {

        Webhook webhook = webhookRepo.findById(webhookId)
                .orElseThrow(WebhookNotFoundException::new);

        WebhookEvent event = new WebhookEvent();

        event.setWebhookId(webhook.getId());
        event.setPayload(payload);

        eventRepo.save(event);

        send(webhook, event);
    }

    public void send(Webhook webhook,
                     WebhookEvent event) {

        try {

            HttpHeaders headers = new HttpHeaders();

            headers.setContentType(MediaType.APPLICATION_JSON);

            if (webhook.getSecret() != null) {
                headers.set("X-WEBHOOK-SECRET",
                        webhook.getSecret());
            }

            HttpEntity<String> entity =
                    new HttpEntity<>(event.getPayload(), headers);

            restTemplate.exchange(
                    webhook.getUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            event.setDelivered(true);

        } catch (Exception e) {

            event.setAttempts(event.getAttempts() + 1);
        }

        eventRepo.save(event);
    }

    public List<WebhookEvent> failedEvents() {
        return eventRepo.findByDeliveredFalse();
    }
}