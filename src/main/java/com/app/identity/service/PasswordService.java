package com.app.identity.service;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.app.config.AppProperties;
import com.app.core.exception.InvalidTokenException;
import com.app.core.exception.UserNotFoundException;
import com.app.core.security.jwt.JwtService;
import com.app.email.service.EmailService;
import com.app.email.service.EmailTemplateService;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;

@Service
public class PasswordService {

	private final JwtService jwt;
	private final UserRepository repo;
	private final EmailService emailService;
	private final EmailTemplateService templateService;
	private final PasswordEncoder passwordEncoder;
	private final AppProperties appProperties;

	public PasswordService(JwtService jwt, UserRepository repo, EmailService emailService,
			EmailTemplateService templateService, PasswordEncoder passwordEncoder, AppProperties appProperties) {
		this.jwt = jwt;
		this.repo = repo;
		this.emailService = emailService;
		this.templateService = templateService;
		this.passwordEncoder = passwordEncoder;
		this.appProperties = appProperties;
	}

	public void sendResetLink(String email) {
		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);

		String token = jwt.generateResetToken(email);

		String resetBaseUrl = appProperties.getFrontendUrl() + "/reset-password.html";
		String link = resetBaseUrl + "?token=" + token;

		// Calculate expiry in minutes (from the validity property)
		long validityMs = jwt.getResetTokenValidity(); // you'd need a getter in JwtService
		long expiryMinutes = validityMs / 60000; // convert to minutes

		Map<String, Object> params = Map.of("name", user.getName(), "link", link, "expiryMinutes", expiryMinutes,
				"appUrl", appProperties.getFrontendUrl());

		String html = templateService.process("email/reset-password", params);
		emailService.sendHtml(user.getEmail(), "Reset Password", html);
	}

	public void resetPassword(String token, String newPassword) {
		if (!jwt.isResetTokenValid(token)) {
			throw new InvalidTokenException();
		}
		String email = jwt.extractEmail(token);
		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);
		user.setPassword(passwordEncoder.encode(newPassword));
		repo.save(user);
	}
}