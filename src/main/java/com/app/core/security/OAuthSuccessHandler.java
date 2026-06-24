package com.app.core.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.app.identity.service.OAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OAuthSuccessHandler implements AuthenticationSuccessHandler {

	private final OAuthService service;

	public OAuthSuccessHandler(OAuthService service) {
		this.service = service;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {

		OAuth2User user = (OAuth2User) authentication.getPrincipal();

		String token = service.processGoogleUser(user);

		response.sendRedirect("http://localhost:3000/oauth-success?token=" + token);
	}
}