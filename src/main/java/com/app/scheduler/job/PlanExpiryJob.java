package com.app.scheduler.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.billing.entity.Subscription;
import com.app.billing.repository.SubscriptionRepository;

@Component
public class PlanExpiryJob {

    private final SubscriptionRepository repo;

    public PlanExpiryJob(SubscriptionRepository repo) {
        this.repo = repo;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void expirePlans() {

        List<Subscription> list = repo.findAll();

        for (Subscription sub : list) {

            if (sub.isActive() &&
                    sub.getExpiryDate()
                            .isBefore(LocalDateTime.now())) {

                sub.setActive(false);

                repo.save(sub);
            }
        }
    }
}