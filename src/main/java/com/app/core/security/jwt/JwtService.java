package com.app.core.security.jwt;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.app.config.SecurityProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtService {

	private final SecretKey key;
	private final long accessValidity;
	private final long resetValidity;
	private final boolean redisEnabled;

	private final StringRedisTemplate redis;

	private static final String PREFIX = "reset_token:";

	public JwtService(SecurityProperties props, StringRedisTemplate redis) {
		this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes());
		this.accessValidity = props.getAccessTokenValidity();
		this.resetValidity = props.getResetTokenValidity();
		this.redisEnabled = props.isRedisEnabled();
		this.redis = redis;
	}

	// ================= ACCESS TOKEN =================
	public String generateAccessToken(String email, String role) {

		return Jwts.builder().setSubject(email).claim("type", "ACCESS").claim("role", role).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + accessValidity)).signWith(key).compact();
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

			// 🔥 Redis check (optional)
			if (redisEnabled) {

				String tokenId = (String) claims.get("tokenId");

				if (tokenId == null)
					return false;

				String key = PREFIX + tokenId;

				// already used
				if (Boolean.TRUE.equals(redis.hasKey(key))) {
					return false;
				}

				// mark used
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

			boolean result = expectedType.equals(claims.get("type")) && !expired;

			return result;

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
}