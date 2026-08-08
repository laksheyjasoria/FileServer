package com.app.orchestrator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

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

	public String google(String idToken) {
		return service.googleLogin(idToken);
	}

	public com.app.identity.entity.User updateProfile(String email, String name, String photoUrl) {
		return service.updateProfile(email, name, photoUrl);
	}

	public void changePassword(String email, String oldPassword, String newPassword) {
		service.changePassword(email, oldPassword, newPassword);
	}

	public String uploadProfilePhoto(String email, MultipartFile file) throws java.io.IOException {
		return service.uploadProfilePhoto(email, file);
	}
}