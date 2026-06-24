package com.app.webhook.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.webhook.entity.Webhook;

public interface WebhookRepository
        extends JpaRepository<Webhook, String> {

    List<Webhook> findByUserId(String userId);

    List<Webhook> findByEnabledTrue();
}