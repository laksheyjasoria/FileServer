package com.app.identity.service;

import java.time.LocalDateTime;

import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.app.core.exception.OAuthException;
import com.app.core.security.jwt.JwtService;
import com.app.identity.entity.User;
import com.app.identity.enums.AuthProvider;
import com.app.identity.enums.Role;
import com.app.identity.repository.UserRepository;

@Service
public class OAuthService {

	private final UserRepository repo;
	private final JwtService jwt;

	public OAuthService(UserRepository repo, JwtService jwt) {
		this.repo = repo;
		this.jwt = jwt;
	}

	public String processGoogleUser(OAuth2User oAuth2User) {

		String email = oAuth2User.getAttribute("email");
		String name = oAuth2User.getAttribute("name");

		if (email == null || email.isBlank()) {
			throw new OAuthException("Invalid Google user data: email missing");
		}

		User user = repo.findByEmail(email).orElseGet(() -> {
			User u = new User();
			u.setEmail(email);
			u.setName(name);
			u.setProvider(AuthProvider.GOOGLE);
			u.setRole(Role.USER);
			u.setEnabled(true);
			u.setCreatedAt(LocalDateTime.now());
			return repo.save(u);
		});

		return jwt.generateAccessToken(user.getEmail(), user.getRole().name());
	}
}