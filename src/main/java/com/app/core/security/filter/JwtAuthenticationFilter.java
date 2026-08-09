package com.app.core.security.filter;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.core.security.jwt.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
	private final JwtService jwt;
	private final String masterKey;

	public JwtAuthenticationFilter(JwtService jwt, String masterKey) {
		this.jwt = jwt;
		this.masterKey = masterKey;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		String path = request.getRequestURI();

		// 1. Public endpoints – skip authentication
		if (isPublic(path)) {
			chain.doFilter(request, response);
			return;
		}

		// 2. Handle Master Key / Admin special endpoints
		if (path.startsWith("/logger/") || path.startsWith("/admin/")) {
			if (handleMasterKeyOrAdmin(request, response, chain)) {
				return; // request was handled
			}
			// If not handled, send 401 (handled inside method)
			return;
		}

		// 3. Regular JWT authentication for other APIs
		handleRegularJwt(request);
		chain.doFilter(request, response);
	}

	// ---------------------- Helper Methods ----------------------

	private boolean isPublic(String path) {
		// Auth endpoints
		if (path.startsWith("/auth")) {
			return true;
		}
		// Public logger log/error endpoints (exact match)
		if (path.equals("/logger/log") || path.equals("/logger/error")) {
			return true;
		}
		// Static resources
		if (path.startsWith("/js/") || path.startsWith("/js2/") || path.startsWith("/css/")
				|| path.startsWith("/images/") || path.startsWith("/assets/") || path.equals("/favicon.ico")
				|| path.equals("/") || path.equals("/index.html")) {
			return true;
		}
		// Common static file extensions
		return path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".json")
				|| path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif")
				|| path.endsWith(".svg") || path.endsWith(".ico") || path.endsWith(".woff") || path.endsWith(".woff2")
				|| path.endsWith(".ttf");
	}

	/**
	 * Attempts to authenticate with Master Key or Admin JWT for /logger/ and
	 * /admin/ endpoints. Returns true if authentication succeeded, false otherwise.
	 * If authentication fails, sends 401 response.
	 */
	private boolean handleMasterKeyOrAdmin(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		String path = request.getRequestURI();
		String method = request.getMethod();

		// ---------- Master Key (only if not DELETE on /admin/) ----------
		boolean isAdminDelete = path.startsWith("/admin/") && "DELETE".equalsIgnoreCase(method);
		if (!isAdminDelete) {
			String masterKeyHeader = request.getHeader("X-Master-Key");
			if (masterKeyHeader != null && masterKeyHeader.equals(masterKey)) {
				// Master key is valid – set as ADMIN (since ROLE_MASTER does not exist)
				UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("master", null,
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
				SecurityContextHolder.getContext().setAuthentication(auth);
				chain.doFilter(request, response);
				return true;
			}
		}

		// ---------- Admin JWT (for all /admin/ endpoints, including DELETE) ----------
		if (path.startsWith("/admin/")) {
			return handleAdminJwt(request, response, chain);
		}

		// For /logger/ endpoints (only Master Key works, so if it fails, we return
		// false)
		// But we already tried Master Key; if it wasn't valid, send 401.
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing Master Key");
		return false;
	}

	/**
	 * Validates that the request contains a valid Admin JWT. Returns true and sets
	 * authentication if successful, otherwise sends 401 and returns false.
	 */
	private boolean handleAdminJwt(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7);
			if (jwt.isAccessTokenValid(token)) {
				String role = jwt.extractRole(token);
				if ("ADMIN".equalsIgnoreCase(role)) {
					String email = jwt.extractEmail(token);
					UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null,
							List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
					SecurityContextHolder.getContext().setAuthentication(auth);
					chain.doFilter(request, response);
					return true;
				}
			}
		}

		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing Admin token");
		return false;
	}

	/**
	 * Attempts to authenticate a regular JWT (any role) for normal API endpoints.
	 * Sets authentication if valid, otherwise leaves context unauthenticated.
	 */
	private void handleRegularJwt(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7);
			try {
				if (jwt.isAccessTokenValid(token)) {
					String email = jwt.extractEmail(token);
					String role = jwt.extractRole(token);
					UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null,
							List.of(new SimpleGrantedAuthority("ROLE_" + role)));
					SecurityContextHolder.getContext().setAuthentication(auth);
				}
			} catch (io.jsonwebtoken.ExpiredJwtException e) {
				logger.debug("Expired JWT token: {}", e.getMessage());
			} catch (Exception e) {
				logger.debug("Invalid JWT token: {}", e.getMessage());
			}
		}
	}
}