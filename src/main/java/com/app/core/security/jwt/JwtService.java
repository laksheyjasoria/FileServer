package com.app.core.security.jwt;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.app.config.SecurityProperties;
import com.app.core.exception.InvalidTokenException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

	private final SecretKey key;
	private final long accessValidity;
	private final long rememberMeValidity;
	private final long resetValidity;
	private final boolean redisEnabled;

	private final StringRedisTemplate redis;

	private static final String PREFIX = "reset_token:";

	// ---- File token default validity (from config) ----
	@Value("${app.file.token.validity-seconds:2592000}")
	private long fileTokenValiditySeconds; // 30 days default

	public JwtService(SecurityProperties props, StringRedisTemplate redis) {
		this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes());
		this.accessValidity = props.getAccessTokenValidity();
		this.resetValidity = props.getResetTokenValidity();
		this.rememberMeValidity = props.getRememberMeValidity();
		this.redisEnabled = props.isRedisEnabled();
		this.redis = redis;
	}

	// ================= ACCESS TOKEN =================
	public String generateAccessToken(String email, String role) {
		return generateAccessToken(email, role, false);
	}

	public String generateAccessToken(String email, String role, boolean rememberMe) {
		long validity = rememberMe ? rememberMeValidity : accessValidity;
		return Jwts.builder().setSubject(email).claim("type", "ACCESS").claim("role", role).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + validity)).signWith(key).compact();
	}

	// ================= RESET TOKEN =================
	public String generateResetToken(String email) {
		return Jwts.builder().setSubject(email).claim("type", "RESET").claim("tokenId", UUID.randomUUID().toString())
				.setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + resetValidity))
				.signWith(key).compact();
	}

	// ================= VALIDATION =================
	public boolean isAccessTokenValid(String token) {
		return validate(token, "ACCESS");
	}

	public boolean isResetTokenValid(String token) {
		try {
			Claims claims = extractAllClaims(token);

			if (!"RESET".equals(claims.get("type"))) {
				return false;
			}

			if (isTokenExpired(claims)) {
				return false;
			}

			// Redis check (optional)
			if (redisEnabled) {
				String tokenId = (String) claims.get("tokenId");
				if (tokenId == null)
					return false;
				String key = PREFIX + tokenId;
				if (Boolean.TRUE.equals(redis.hasKey(key))) {
					return false;
				}
				long ttl = claims.getExpiration().getTime() - System.currentTimeMillis();
				redis.opsForValue().set(key, "USED", Duration.ofMillis(ttl));
			}

			return true;

		} catch (Exception e) {
			return false;
		}
	}

	private boolean validate(String token, String expectedType) {
		try {
			Claims claims = extractAllClaims(token);
			boolean expired = isTokenExpired(claims);
			return expectedType.equals(claims.get("type")) && !expired;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	// ================= EXTRACT =================
	public String extractEmail(String token) {
		return extractAllClaims(token).getSubject();
	}

	public String extractRole(String token) {
		return (String) extractAllClaims(token).get("role");
	}

	public String extractTokenId(String token) {
		return (String) extractAllClaims(token).get("tokenId");
	}

	public Date extractExpiration(String token) {
		return extractAllClaims(token).getExpiration();
	}

	// ================= INTERNAL =================
	private Claims extractAllClaims(String token) {
		return parse(token).getBody();
	}

	private boolean isTokenExpired(Claims claims) {
		return claims.getExpiration().before(new Date());
	}

	private Jws<Claims> parse(String token) {
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
	}

	public long getResetTokenValidity() {
		return resetValidity;
	}

	// ============================================================
	// FILE ACCESS TOKENS (overloaded)
	// ============================================================

	/**
	 * Generate a file access token with the default validity (from config).
	 * 
	 * @param fileId the file identifier
	 * @return signed JWT
	 */
	public String generateFileAccessToken(String fileId) {
		return generateFileAccessToken(fileId, fileTokenValiditySeconds);
	}

	/**
	 * Generate a file access token with a custom validity period.
	 * 
	 * @param fileId          the file identifier
	 * @param validitySeconds number of seconds the token should be valid
	 * @return signed JWT
	 */
	public String generateFileAccessToken(String fileId, long validitySeconds) {
		return Jwts.builder().setSubject(fileId).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + validitySeconds * 1000)).signWith(key).compact();
	}

	/**
	 * Generate a file access token with a very long validity (10 years).
	 * 
	 * @param fileId the file identifier
	 * @return signed JWT (valid for ~10 years)
	 */
	public String generateFileAccessTokenInfinite(String fileId) {
		long tenYears = 10L * 365 * 24 * 60 * 60; // 10 years in seconds
		return generateFileAccessToken(fileId, tenYears);
	}

	public String generateFileAccessToken(String fileId, Date expiration) {
		return Jwts.builder().setSubject(fileId).setIssuedAt(new Date()).setExpiration(expiration).signWith(key)
				.compact();
	}

	/**
	 * Validate the file access token and extract the fileId.
	 * 
	 * @param token The signed JWT
	 * @return The fileId if valid
	 * @throws InvalidTokenException if token is invalid or expired
	 */
	public String validateFileAccessToken(String token) {
		try {
			Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
			return claims.getSubject();
		} catch (Exception e) {
			throw new RuntimeException("Invalid or expired file access token");
		}
	}
}