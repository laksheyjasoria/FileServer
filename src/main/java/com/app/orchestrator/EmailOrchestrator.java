package com.app.orchestrator;

import org.springframework.stereotype.Component;

import com.app.email.service.EmailService;

@Component
public class EmailOrchestrator {

	private final EmailService emailService;

//    private final AppLogger log =
//            AppLogger.getLogger("EMAIL");

	public EmailOrchestrator(EmailService emailService) {
		this.emailService = emailService;
	}

	public void send(String to, String subject, String body) {

		emailService.send(to, subject, body);

//        log.info("Email sent to {}", to);
	}
}