package com.app.identity.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.Authentication;

import com.app.core.response.ApiResponse;
import com.app.identity.dto.LoginRequest;
import com.app.identity.dto.RegisterRequest;
import com.app.identity.service.PasswordService;
import com.app.orchestrator.AuthOrchestrator;
import com.app.identity.repository.UserRepository;
import com.app.identity.entity.User;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthOrchestrator orchestrator;
	private final PasswordService passwordService;
	private final UserRepository userRepository;

	public AuthController(AuthOrchestrator orchestrator, PasswordService passwordService,
			UserRepository userRepository) {
		this.orchestrator = orchestrator;
		this.passwordService = passwordService;
		this.userRepository = userRepository;
	}

	@PostMapping("/register")
	public ApiResponse<String> register(@Valid @RequestBody RegisterRequest req) {
		return ApiResponse.success(orchestrator.register(req.getEmail(), req.getPassword(), req.getName()));
	}

	@PostMapping("/login")
	public ApiResponse<String> login(@RequestBody LoginRequest req) {
		return ApiResponse.success(orchestrator.login(req.getEmail(), req.getPassword()));
	}

	@PostMapping("/forgot-password")
	public ApiResponse<String> forgot(@RequestParam String email) {
		passwordService.sendResetLink(email);
		return ApiResponse.success("Reset email sent");
	}

	@PostMapping("/reset-password")
	public ApiResponse<String> reset(@RequestParam String token, @RequestParam String password) {
		passwordService.resetPassword(token, password);
		return ApiResponse.success("Password updated");
	}

	@GetMapping("/me")
	public ApiResponse<User> me(Authentication auth) {

		if (auth == null || auth.getName() == null) {
			return ApiResponse.error("Unauthenticated");
		}

		return userRepository.findByEmail(auth.getName())
				.map(ApiResponse::success)
				.orElse(ApiResponse.error("User not found"));
	}
}