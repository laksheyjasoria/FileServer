package com.app.identity.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.app.core.exception.AccountDeactivatedException;
import com.app.core.exception.InvalidCredentialsException;
import com.app.core.exception.UserAlreadyExistsException;
import com.app.core.exception.UserNotFoundException;
import com.app.core.security.jwt.JwtService;
import com.app.core.util.FileStorageUtils;
import com.app.identity.entity.AuthProvider;
import com.app.identity.entity.Role;
import com.app.identity.entity.User;
import com.app.identity.entity.UserStatus;
import com.app.identity.repository.UserRepository;
import com.app.storage.factory.StorageFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final UserRepository repo;
	private final JwtService jwt;
	private final BCryptPasswordEncoder encoder;
	private final StorageFactory storageFactory;
	private final FileStorageUtils fileStorageUtils;

	public AuthService(UserRepository repo, JwtService jwt, BCryptPasswordEncoder encoder,
			StorageFactory storageFactory, FileStorageUtils fileStorageUtils) {
		this.repo = repo;
		this.jwt = jwt;
		this.encoder = encoder;
		this.storageFactory = storageFactory;
		this.fileStorageUtils = fileStorageUtils;
	}

	// ================================
	// REGISTER
	// ================================

	public User register(User user) {
		if (repo.findByEmail(user.getEmail()).isPresent()) {
			throw new UserAlreadyExistsException();
		}
		user.setPassword(encoder.encode(user.getPassword()));
		if (user.getProvider() == null)
			user.setProvider(AuthProvider.LOCAL);
		if (user.getRole() == null)
			user.setRole(Role.USER);
		user.setEnabled(true);
		if (user.getCreatedAt() == null)
			user.setCreatedAt(LocalDateTime.now());
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

	public String register(String email, String password, String name, MultipartFile file) throws java.io.IOException {
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

		User savedUser = repo.save(user);

		if (file != null && !file.isEmpty()) {
			String uniqueFilename = fileStorageUtils.generateUniqueFileName(savedUser.getId(),
					file.getOriginalFilename());
			String fileId = storageFactory.get().upload(file.getBytes(), uniqueFilename);
			savedUser.setPhotoUrl(fileId);
			repo.save(savedUser);
		}

		return jwt.generateAccessToken(savedUser.getEmail(), savedUser.getRole().name());
	}

	// ================================
	// LOGIN
	// ================================

	public String login(String email, String password, boolean isRememberMe) {
		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new AccountDeactivatedException("Account is deactivated or deleted");
		}
		if (user.getProvider() != AuthProvider.LOCAL) {
			throw new InvalidCredentialsException();
		}
		if (!encoder.matches(password, user.getPassword())) {
			throw new InvalidCredentialsException();
		}
		return jwt.generateAccessToken(user.getEmail(), user.getRole().name(), isRememberMe);
	}

	// ================================
	// GOOGLE LOGIN (with sync)
	// ================================

	public String googleLogin(String idToken) {
		return googleLogin(idToken, false);
	}

	public String googleLogin(String idToken, boolean sync) {
		log.info("Google login attempt, sync={}", sync);
		try {
			URL url = new URL("https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);

			if (conn.getResponseCode() != 200) {
				log.warn("Google token verification failed with status: {}", conn.getResponseCode());
				throw new InvalidCredentialsException();
			}

			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> tokenInfo = mapper.readValue(conn.getInputStream(), Map.class);

			String email = (String) tokenInfo.get("email");
			Boolean emailVerified = tokenInfo.get("email_verified") == null ? Boolean.FALSE
					: Boolean.valueOf(tokenInfo.get("email_verified").toString());
			String name = (String) tokenInfo.get("name");
			String googlePictureUrl = (String) tokenInfo.get("picture");

			log.info("Google token info: email={}, verified={}, name={}, pictureUrl={}", email, emailVerified, name,
					googlePictureUrl);

			if (email == null || !emailVerified) {
				log.warn("Email not verified or missing: email={}, verified={}", email, emailVerified);
				throw new InvalidCredentialsException();
			}

			User user = repo.findByEmail(email).orElse(null);
			boolean isNewUser = (user == null);

			if (isNewUser) {
				log.info("Creating new user with email: {}", email);
				user = new User();
				user.setEmail(email);
				user.setName(name != null ? name : email);
				user.setProvider(AuthProvider.GOOGLE);
				user.setRole(Role.USER);
				user.setEnabled(true);
				user.setCreatedAt(LocalDateTime.now());

				String fileId = downloadAndUploadGooglePicture(googlePictureUrl, email);
				user.setPhotoUrl(fileId);
				log.info("New user created, photo fileId: {}", fileId);
				repo.save(user);
			} else {
				log.info("Existing user found: email={}, current photoUrl={}, sync={}", email, user.getPhotoUrl(),
						sync);
				
				if (user.getStatus() != UserStatus.ACTIVE) {
				    log.warn("User {} is not active (status={}), rejecting login", email, user.getStatus());
				    throw new AccountDeactivatedException("Account is deactivated or deleted");
				}

				boolean changed = false;

				if (sync) {
					log.info("Sync mode: forcing update from Google");
					String fileId = downloadAndUploadGooglePicture(googlePictureUrl, email);
					if (fileId != null) {
						user.setPhotoUrl(fileId);
						changed = true;
						log.info("Updated photoUrl to Telegram fileId: {}", fileId);
					} else {
						log.warn("Failed to download/upload Google picture for sync");
					}
					if (name != null && !name.equals(user.getName())) {
						user.setName(name);
						changed = true;
						log.info("Updated name to: {}", name);
					}
					if (changed) {
						repo.save(user);
						log.info("User updated successfully (sync mode)");
					}
				} else {
					// Normal login
					boolean changedNormal = false;
					if (name != null && !name.equals(user.getName())) {
						user.setName(name);
						changedNormal = true;
						log.info("Updated name to: {}", name);
					}

					String currentPhoto = user.getPhotoUrl();
					if (currentPhoto == null || currentPhoto.isBlank() || currentPhoto.startsWith("http")) {
						log.info("Photo URL is null, empty, or external ({}). Migrating to Telegram.", currentPhoto);
						String fileId = downloadAndUploadGooglePicture(googlePictureUrl, email);
						if (fileId != null) {
							user.setPhotoUrl(fileId);
							changedNormal = true;
							log.info("Migrated photoUrl to Telegram fileId: {}", fileId);
						} else {
							log.warn("Migration failed – could not download/upload Google picture");
						}
					} else {
						log.info("Photo URL already a Telegram fileId: {}", currentPhoto);
					}

					if (changedNormal) {
						repo.save(user);
						log.info("User updated successfully (normal login)");
					}
				}
			}

			String token = jwt.generateAccessToken(user.getEmail(), user.getRole().name());
			log.info("Google login successful for email: {}", email);
			return token;

		} catch (Exception e) {
			log.error("Google login failed: {}", e.getMessage(), e);
			throw new InvalidCredentialsException();
		}
	}

	// ================================
	// HELPER: Download Google Picture
	// ================================

	private String downloadAndUploadGooglePicture(String pictureUrl, String email) {
		log.info("Attempting to download Google picture from URL: {}", pictureUrl);
		if (pictureUrl == null || pictureUrl.isBlank()) {
			log.warn("Google picture URL is null or empty");
			return null;
		}
		try {
			URL url = new URL(pictureUrl);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);

			int responseCode = conn.getResponseCode();
			log.info("Download response code: {}", responseCode);
			if (responseCode != 200) {
				log.warn("Failed to download Google picture, status: {}", responseCode);
				return null;
			}

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream in = conn.getInputStream()) {
				byte[] buffer = new byte[4096];
				int bytesRead;
				while ((bytesRead = in.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
			}
			byte[] imageBytes = out.toByteArray();
			if (imageBytes.length == 0) {
				log.warn("Downloaded image is empty (0 bytes)");
				return null;
			}
			log.info("Downloaded {} bytes from Google", imageBytes.length);

			String filename = "profile_" + email + "_" + System.currentTimeMillis() + ".jpg";
			log.info("Uploading to Telegram with filename: {}", filename);
			String fileId = storageFactory.get().upload(imageBytes, filename);
			log.info("Upload successful, Telegram fileId: {}", fileId);
			return fileId;

		} catch (Exception e) {
			log.error("Error downloading/uploading Google picture: {}", e.getMessage(), e);
			return null;
		}
	}

	// ================================
	// UPDATE PROFILE
	// ================================

	public User updateProfile(String email, String name, String photoUrl) {
		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);
		if (name != null && !name.isBlank())
			user.setName(name);
		if (photoUrl != null)
			user.setPhotoUrl(photoUrl);
		return repo.save(user);
	}

	// ================================
	// UPLOAD PROFILE PHOTO (manual)
	// ================================

	public String uploadProfilePhoto(String email, MultipartFile file) throws java.io.IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Please select an image");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			throw new IllegalArgumentException("Only image files are allowed");
		}

		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);
		String uniqueFilename = fileStorageUtils.generateUniqueFileName(user.getId(), file.getOriginalFilename());
		String fileId = storageFactory.get().upload(file.getBytes(), uniqueFilename);

		user.setPhotoUrl(fileId);
		repo.save(user);

		return fileId;
	}

	// ================================
	// CHANGE PASSWORD
	// ================================

	public void changePassword(String email, String oldPassword, String newPassword) {
		User user = repo.findByEmail(email).orElseThrow(UserNotFoundException::new);
		if (!encoder.matches(oldPassword, user.getPassword())) {
			throw new InvalidCredentialsException();
		}
		user.setPassword(encoder.encode(newPassword));
		repo.save(user);
	}

	// ================================
	// GET USER BY EMAIL
	// ================================

	public User getUserByEmail(String email) {
		return repo.findByEmail(email).orElseThrow(UserNotFoundException::new);
	}
}