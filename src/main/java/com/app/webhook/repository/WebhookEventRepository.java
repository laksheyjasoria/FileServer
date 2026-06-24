package com.app.webhook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.webhook.entity.WebhookEvent;

public interface WebhookEventRepository
        extends JpaRepository<WebhookEvent, String> {

    List<WebhookEvent> findByDeliveredFalse();
}