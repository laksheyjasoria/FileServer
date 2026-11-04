package com.app.scheduler.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.webhook.entity.WebhookEvent;
import com.app.webhook.repository.WebhookRepository;
import com.app.webhook.service.WebhookService;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.scheduler.enabled:true} && ${app.scheduler.webhook-retry.enabled:true}")
public class WebhookRetryJob {

	private final WebhookService service;
	private final WebhookRepository webhookRepo;

	public WebhookRetryJob(WebhookService service, WebhookRepository webhookRepo) {
		this.service = service;
		this.webhookRepo = webhookRepo;
	}

	@Scheduled(fixedDelayString = "${app.scheduler.webhook-retry.fixed-delay-ms:300000}")
	public void retryFailed() {

		List<WebhookEvent> failed = service.failedEvents();

		for (WebhookEvent event : failed) {

			webhookRepo.findById(event.getWebhookId()).ifPresent(webhook -> service.send(webhook, event));
		}
	}
}