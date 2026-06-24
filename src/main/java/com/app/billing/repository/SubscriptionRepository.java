package com.app.billing.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.app.billing.entity.Subscription;

public interface SubscriptionRepository
        extends JpaRepository<Subscription, String> {

    Optional<Subscription> findByUserIdAndActiveTrue(String userId);
}