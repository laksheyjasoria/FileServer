package com.app.orchestrator;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.app.identity.entity.User;
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

	public String register(String email, String password, String name, MultipartFile file) throws IOException {
		return service.register(email, password, name, file);
	}

	public String login(String email, String password, boolean isRememberMe) {
		return service.login(email, password, isRememberMe);
	}

	public String google(String idToken) {
		return service.googleLogin(idToken);
	}

	public String google(String idToken, boolean sync) {
		return service.googleLogin(idToken, sync);
	}

	public User updateProfile(String email, String name, String photoUrl) {
		return service.updateProfile(email, name, photoUrl);
	}

	public void changePassword(String email, String oldPassword, String newPassword) {
		service.changePassword(email, oldPassword, newPassword);
	}

	public String uploadProfilePhoto(String email, MultipartFile file) throws IOException {
		return service.uploadProfilePhoto(email, file);
	}
}