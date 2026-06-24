package com.app.identity.service;

import java.time.LocalDateTime;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.app.core.exception.InvalidCredentialsException;
import com.app.core.exception.UserAlreadyExistsException;
import com.app.core.exception.UserNotFoundException;
import com.app.core.security.jwt.JwtService;
import com.app.identity.entity.AuthProvider;
import com.app.identity.entity.Role;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;

@Service
public class AuthService {

	private final UserRepository repo;
	private final JwtService jwt;
	private final BCryptPasswordEncoder encoder;

	public AuthService(UserRepository repo, JwtService jwt, BCryptPasswordEncoder encoder) {
		this.repo = repo;
		this.jwt = jwt;
		this.encoder = encoder;
	}
	
    public User register(User user) {

        if (repo.findByEmail(user.getEmail()).isPresent()) {
            throw new UserAlreadyExistsException();
        }

        user.setPassword(
                encoder.encode(user.getPassword())
        );

        if (user.getProvider() == null) {
            user.setProvider(AuthProvider.LOCAL);
        }

        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }

        user.setEnabled(true);

        if (user.getCreatedAt() == null) {
            user.setCreatedAt(LocalDateTime.now());
        }

        return repo.save(user);
    }

	public String register(String email, String password, String name) {

		if (repo.findByEmail(email).isPresent()) {
			throw new UserAlreadyExistsException();
		}

		User user = new User();
		user.setEmail(email);
		user.setPassword(encoder.encode(password));
		user.setName(name);
		user.setProvider(AuthProvider.LOCAL);
		user.setRole(Role.USER);
		user.setEnabled(true);
		user.setCreatedAt(LocalDateTime.now());

		repo.save(user);

		return jwt.generateAccessToken(user.getEmail(), user.getRole().name());
	}

	public String login(String email, String password) {

		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);

		if (user.getProvider() != AuthProvider.LOCAL) {
			throw new InvalidCredentialsException();
		}

		if (!encoder.matches(password, user.getPassword())) {
			throw new InvalidCredentialsException();
		}

		return jwt.generateAccessToken(user.getEmail(), user.getRole().name());
	}
}