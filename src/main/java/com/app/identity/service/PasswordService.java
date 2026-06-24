package com.app.identity.service;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

	// 🔥 configurable frontend URL
	private final String resetBaseUrl = "http://localhost:3000/reset-password";

	public PasswordService(JwtService jwt, UserRepository repo, EmailService emailService,
			EmailTemplateService templateService, PasswordEncoder passwordEncoder) {
		this.jwt = jwt;
		this.repo = repo;
		this.emailService = emailService;
		this.templateService = templateService;
		this.passwordEncoder = passwordEncoder;
	}

	// ================= SEND RESET LINK =================
	public void sendResetLink(String email) {

		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);

		String token = jwt.generateResetToken(email);

		String link = resetBaseUrl + "?token=" + token;

		String html = templateService.process("reset-password", Map.of("name", user.getName(), "link", link));

		emailService.sendHtml(user.getEmail(), "Reset Password", html);
	}

	// ================= RESET PASSWORD =================
	public void resetPassword(String token, String newPassword) {

		// 1. Validate token
		if (!jwt.isResetTokenValid(token)) {
			throw new InvalidTokenException();
		}

		// 2. Extract email
		String email = jwt.extractEmail(token);

		// 3. Fetch user
		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);

		// 4. Update password
		user.setPassword(passwordEncoder.encode(newPassword));

		repo.save(user);
	}
}