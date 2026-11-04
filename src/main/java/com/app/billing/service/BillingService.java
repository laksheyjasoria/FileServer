package com.app.billing.service;

import java.time.LocalDateTime;
import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.billing.dto.AssignPlanRequest;
import com.app.billing.dto.CreatePlanRequest;
import com.app.billing.entity.Plan;
import com.app.billing.entity.Subscription;
import com.app.billing.repository.PlanRepository;
import com.app.billing.repository.SubscriptionRepository;
import com.app.core.exception.PlanNotFoundException;
import com.app.core.exception.SubscriptionNotFoundException;

@Service
public class BillingService {

    private final PlanRepository planRepo;
    private final SubscriptionRepository subRepo;

    public BillingService(PlanRepository planRepo,
                          SubscriptionRepository subRepo) {
        this.planRepo = planRepo;
        this.subRepo = subRepo;
    }

    public Plan createPlan(CreatePlanRequest request) {

        Plan plan = new Plan();

        plan.setName(request.getName());
        plan.setStorageLimitBytes(request.getStorageLimitBytes());
        plan.setMaxUploadSizeBytes(request.getMaxUploadSizeBytes());
        plan.setDailyUploadLimit(request.getDailyUploadLimit());
        plan.setApiRequestLimit(request.getApiRequestLimit());
        plan.setPrice(request.getPrice());
        plan.setActive(true);

        return planRepo.save(plan);
    }

    @Transactional
    public Subscription assignPlan(AssignPlanRequest request) {

        Plan plan = planRepo.findById(request.getPlanId())
                .orElseThrow(PlanNotFoundException::new);

        subRepo.findByUserIdAndActiveTrue(request.getUserId()).ifPresent(existing -> {
            existing.setActive(false);
            subRepo.save(existing);
        });

        Subscription sub = new Subscription();

        sub.setUserId(request.getUserId());
        sub.setPlan(plan);
        sub.setStartDate(LocalDateTime.now());

        sub.setExpiryDate(
                LocalDateTime.now()
                        .plusDays(request.getValidityDays())
        );

        sub.setActive(true);

        return subRepo.save(sub);
    }

    public Subscription getActiveSubscription(String userId) {

        return subRepo.findByUserIdAndActiveTrue(userId)
                .orElseThrow(SubscriptionNotFoundException::new);
    }

    /** Ensures every registered user can upload without manual plan assignment. */
    @Transactional
    public Subscription getOrCreateActiveSubscription(String userId) {
        return subRepo.findByUserIdAndActiveTrue(userId)
                .map(subscription -> {
                    LocalDateTime expiry = subscription.getExpiryDate();
                    if (expiry != null && expiry.isAfter(LocalDateTime.now())) {
                        return subscription;
                    }
                    subscription.setActive(false);
                    subRepo.save(subscription);
                    return createFreeSubscription(userId);
                })
                .orElseGet(() -> createFreeSubscription(userId));
    }

    private Subscription createFreeSubscription(String userId) {
        Plan plan = planRepo.findByName("FREE").orElseGet(() -> {
            Plan free = new Plan();
            free.setName("FREE");
            free.setStorageLimitBytes(1_073_741_824L);
            free.setMaxUploadSizeBytes(104_857_600L);
            free.setDailyUploadLimit(100);
            free.setApiRequestLimit(10_000);
            free.setPrice(BigDecimal.ZERO);
            free.setActive(true);
            return planRepo.save(free);
        });

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlan(plan);
        subscription.setStartDate(LocalDateTime.now());
        subscription.setExpiryDate(LocalDateTime.now().plusYears(1));
        subscription.setActive(true);
        return subRepo.save(subscription);
    }

    public boolean hasValidPlan(String userId) {
        return subRepo.findByUserIdAndActiveTrue(userId)
                .map(sub -> sub.getExpiryDate() != null
                        && sub.getExpiryDate().isAfter(LocalDateTime.now()))
                .orElse(false);
    }
}
