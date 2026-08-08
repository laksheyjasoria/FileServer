package com.app.core.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.app.core.security.filter.JwtAuthenticationFilter;
import com.app.core.security.jwt.JwtService;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
public class SecurityConfig {

	private final JwtService jwtService;

	public SecurityConfig(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	// ================= JWT FILTER =================
	@Bean
	public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
			@Value("${app.security.master.key}") String masterKey) {
		return new JwtAuthenticationFilter(jwtService, masterKey);
	}

	// ================= CORS =================
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {

		CorsConfiguration config = new CorsConfiguration();

		config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://your-domain.com"));

		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

		config.setAllowedHeaders(List.of("*"));

		config.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

		source.registerCorsConfiguration("/**", config);

		return source;
	}

	// ================= SECURITY CHAIN =================
	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {

		return http

				// Disable CSRF for JWT based authentication
				.csrf(csrf -> csrf.disable())

				// Stateless session
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

				// CORS
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))

				// Authorization
				.authorizeHttpRequests(auth -> auth

						// Public frontend pages
						.requestMatchers("/", "/index.html", "/login.html", "/signup.html", "/profile.html",
								"/viewer.html", "/share.html", "/share2.html", "/favicon.ico",

								// Static resources
								"/css/**", "/js/**", "/js2/**",

								// Authentication APIs
								"/auth/**", "/share/**",
								
								// PUBLIC LOGGER ENDPOINTS (NO AUTH REQUIRED)
								"/logger/log", "/logger/error")
						.permitAll()

						// Protected APIs
						.requestMatchers("/api/**").authenticated()

						// Everything else
						.anyRequest().authenticated())

				// JWT Filter
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

				// Unauthorized response
				.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {

					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

					response.setContentType("application/json");

					response.getWriter().write("""
							{
							    "success": false,
							    "message": "Unauthorized or token expired"
							}
							""");
				}))

				.build();
	}
}
