package com.app.core.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

	// ================= JWT FILTER BEAN =================
	@Bean
	JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
			@Value("${app.security.master.key}") String masterKey) {
		return new JwtAuthenticationFilter(jwtService, masterKey);
	}

	// ================= CORS =================
	@Bean
	CorsConfigurationSource corsConfigurationSource() {
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
	SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
		return http.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/index.html", "/login.html", "/signup.html", "/profile.html",
								"/viewer.html", "/share.html", "/shared.html", "/shared-by-me.html", "/share2.html",
								"/master.html", "/logger.html", "/favicon.ico", "/css/**", "/js/**", "/js2/**",
								"/auth/**", "/logger/log", "/logger/error", "/download/bulk/shared", "/assets/**")
						.permitAll()

						// 🔐 Authenticated share endpoints
						.requestMatchers("/share/shared-with-me").authenticated().requestMatchers("/share/shared-by-me")
						.authenticated().requestMatchers(HttpMethod.DELETE, "/share/{token}").authenticated()

						// 🌐 Public share viewing/streaming/downloading
						.requestMatchers("/share/**").permitAll()

						.anyRequest().authenticated())
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
					response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
					response.setContentType("application/json");
					response.getWriter().write("""
							{
							    "success": false,
							    "message": "Unauthorized or token expired"
							}
							""");
				})).build();
	}
}