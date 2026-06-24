package com.app.orchestrator;

import org.springframework.stereotype.Component;

import com.app.identity.service.AuthService;

@Component
public class AuthOrchestrator {

	private final AuthService service;

	public AuthOrchestrator(AuthService service) {
		this.service = service;
	}

	public String register(String email, String password, String name) {
		return service.register(email, password, name);
	}

	public String login(String email, String password) {
		return service.login(email, password);
	}
}