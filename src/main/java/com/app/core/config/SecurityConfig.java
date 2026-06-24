package com.app.core.config;

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

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtService jwtService;

    public SecurityConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // ================= JWT FILTER =================
    @Bean
    public JwtAuthenticationFilter jwtFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    // ================= CORS =================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    // ================= SECURITY CHAIN =================
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtFilter) throws Exception {

        return http

                // disable csrf
                .csrf(csrf -> csrf.disable())

                // JWT stateless
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // AUTH RULES
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**",
                                "/login.html",
                                "/index.html",
                                "/css/**",
                                "/js/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                // JWT FILTER
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // ❌ NO oauth2Login (REMOVED)

                // ================= UNAUTHORIZED HANDLING =================
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {

                            // Redirect to login page (NOT Google)
                            response.sendRedirect("/login.html");
                        })
                )

                .build();
    }
}