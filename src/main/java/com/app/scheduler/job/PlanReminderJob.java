package com.app.scheduler.job;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.app.billing.entity.Subscription;
import com.app.billing.repository.SubscriptionRepository;
import com.app.email.service.EmailService;
import com.app.identity.repository.UserRepository;

@Component
public class PlanReminderJob {

	private final SubscriptionRepository subRepo;
	private final UserRepository userRepo;
	private final EmailService emailService;

	public PlanReminderJob(SubscriptionRepository subRepo, UserRepository userRepo, EmailService emailService) {
		this.subRepo = subRepo;
		this.userRepo = userRepo;
		this.emailService = emailService;
	}

	@Scheduled(cron = "0 0 9 * * *")
	public void remind() {

		List<Subscription> list = subRepo.findAll();

		for (Subscription sub : list) {

			if (sub.isActive() && sub.getExpiryDate().isBefore(LocalDateTime.now().plusDays(3))) {

				userRepo.findByEmail(sub.getUserId()).ifPresent(user ->

				emailService.send(user.getEmail(), "Plan Expiry Reminder", "Your plan will expire soon."));
			}
		}
	}
}