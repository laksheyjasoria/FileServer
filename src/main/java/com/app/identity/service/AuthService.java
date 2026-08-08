package com.app.identity.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.exception.InvalidCredentialsException;
import com.app.core.exception.UserAlreadyExistsException;
import com.app.core.exception.UserNotFoundException;
import com.app.core.security.jwt.JwtService;
import com.app.identity.entity.AuthProvider;
import com.app.identity.entity.Role;
import com.app.identity.entity.User;
import com.app.identity.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.util.Map;

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
				encoder.encode(user.getPassword()));

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

	public String googleLogin(String idToken) {

		try {
			// Verify token with Google
			URL url = new URL("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);

			int code = conn.getResponseCode();
			if (code != 200) {
				throw new com.app.core.exception.InvalidCredentialsException();
			}

			InputStream is = conn.getInputStream();
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> tokenInfo = mapper.readValue(is, Map.class);

			String email = (String) tokenInfo.get("email");
			Boolean emailVerified = tokenInfo.get("email_verified") == null ? Boolean.FALSE
					: Boolean.valueOf(tokenInfo.get("email_verified").toString());
			String name = (String) tokenInfo.get("name");
			String picture = (String) tokenInfo.get("picture");

			if (email == null || !emailVerified) {
				throw new com.app.core.exception.InvalidCredentialsException();
			}

			User user = repo.findByEmail(email).orElse(null);

			if (user == null) {
				// Register new Google user
				User u = new User();
				u.setEmail(email);
				u.setName(name != null ? name : email);
				u.setPhotoUrl(picture);
				u.setProvider(AuthProvider.GOOGLE);
				u.setRole(Role.USER);
				u.setEnabled(true);
				u.setCreatedAt(LocalDateTime.now());

				repo.save(u);
				return jwt.generateAccessToken(u.getEmail(), u.getRole().name());
			} else {
				// Existing user: if provider is GOOGLE or LOCAL, allow login; update photo and
				// name if changed
				if (user.getProvider() == null || user.getProvider() == AuthProvider.LOCAL) {
					// keep existing provider; still allow login for convenience
				}
				boolean changed = false;
				if (picture != null && !picture.equals(user.getPhotoUrl())) {
					user.setPhotoUrl(picture);
					changed = true;
				}
				if (name != null && !name.equals(user.getName())) {
					user.setName(name);
					changed = true;
				}
				if (changed)
					repo.save(user);

				return jwt.generateAccessToken(user.getEmail(), user.getRole().name());
			}

		} catch (Exception e) {
			throw new com.app.core.exception.InvalidCredentialsException();
		}
	}

	public User updateProfile(String email, String name, String photoUrl) {
		User user = repo.findByEmail(email).orElseThrow(com.app.core.exception.UserNotFoundException::new);
		if (name != null && !name.isBlank())
			user.setName(name);
		if (photoUrl != null)
			user.setPhotoUrl(photoUrl);
		return repo.save(user);
	}

	public String uploadProfilePhoto(String email, MultipartFile file) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Please select an image");
		}

		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("Only image files are allowed");
		}

		Path storageDir = Paths.get("uploads", "profile-photos").toAbsolutePath().normalize();
		Files.createDirectories(storageDir);

		String originalName = file.getOriginalFilename();
		String extension = "";
		if (originalName != null && originalName.contains(".")) {
			extension = originalName.substring(originalName.lastIndexOf('.'));
		}

		String safeEmail = email.replaceAll("[^a-zA-Z0-9._-]", "_");
		String fileName = safeEmail + "_" + UUID.randomUUID() + extension;
		Path target = storageDir.resolve(fileName);
		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

		String photoUrl = "/uploads/profile-photos/" + fileName;
		User user = repo.findByEmail(email).orElseThrow(com.app.core.exception.UserNotFoundException::new);
		user.setPhotoUrl(photoUrl);
		repo.save(user);
		return photoUrl;
	}

	public void changePassword(String email, String oldPassword, String newPassword) {
		User user = repo.findByEmail(email).orElseThrow(com.app.core.exception.UserNotFoundException::new);
		if (!encoder.matches(oldPassword, user.getPassword())) {
			throw new com.app.core.exception.InvalidCredentialsException();
		}
		user.setPassword(encoder.encode(newPassword));
		repo.save(user);
	}
}