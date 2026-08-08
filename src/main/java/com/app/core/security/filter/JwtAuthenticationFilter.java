package com.app.core.security.filter;

import java.io.IOException;
import java.util.List;

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

	private final JwtService jwt;

	public JwtAuthenticationFilter(JwtService jwt) {
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		try {

			String path = request.getRequestURI();

			// Skip public endpoints and static resources
			if (isPublic(request)) {
				chain.doFilter(request, response);
				return;
			}

			String header = request.getHeader("Authorization");

			if (header != null && header.startsWith("Bearer ")) {

				String token = header.substring(7);

				boolean valid = jwt.isAccessTokenValid(token);

				if (valid) {

					String email = jwt.extractEmail(token);
					String role = jwt.extractRole(token);

					UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null,
							List.of(new SimpleGrantedAuthority("ROLE_" + role)));

					SecurityContextHolder.getContext().setAuthentication(auth);

				}

			}

		} catch (Exception e) {
			logger.error("JWT Authentication Filter error: ", e);
			e.printStackTrace();
		}

		chain.doFilter(request, response);
	}

	private boolean isPublic(HttpServletRequest request) {

		String path = request.getRequestURI();

		// Authentication/public APIs
		if (path.startsWith("/auth")) {
			return true;
		}

		// Logger APIs
		if (path.startsWith("/logger/log") || path.startsWith("/logger/error")) {
			return true;
		}

		// Static frontend resources
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
}