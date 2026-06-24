package com.app.orchestrator;

import org.springframework.stereotype.Component;

import com.app.email.service.EmailService;
import com.app.identity.entity.User;
import com.app.identity.service.AuthService;

@Component
public class UserOrchestrator {

	private final AuthService authService;
	private final EmailService emailService;

//    private final AppLogger log =
//            AppLogger.getLogger("USER");

	public UserOrchestrator(AuthService authService, EmailService emailService) {
		this.authService = authService;
		this.emailService = emailService;
	}

	public User register(User user) {

		User created = authService.register(user);

		emailService.send(created.getEmail(), "Welcome", "Welcome to File Server");

//        log.info("New user registered: {}",
//                created.getEmail());

		return created;
	}
}