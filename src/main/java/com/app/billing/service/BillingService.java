package com.app.billing.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

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

    public Subscription assignPlan(AssignPlanRequest request) {

        Plan plan = planRepo.findById(request.getPlanId())
                .orElseThrow(PlanNotFoundException::new);

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

    public boolean hasValidPlan(String userId) {

        try {

            Subscription sub = getActiveSubscription(userId);

            return sub.getExpiryDate()
                    .isAfter(LocalDateTime.now());

        } catch (Exception e) {
            return false;
        }
    }
}