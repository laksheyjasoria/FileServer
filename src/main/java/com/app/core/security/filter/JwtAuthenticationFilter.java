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

        try {
            String path = request.getRequestURI();

            // 1. Skip public endpoints (auth, static, and logger logging endpoints)
            if (isPublic(request)) {
                chain.doFilter(request, response);
                return;
            }

            // 2. Check Master Key for /logger/** endpoints (excluding /log and /error)
            if (path.startsWith("/logger/")) {
                String masterKeyHeader = request.getHeader("X-Master-Key");
                System.out.println(masterKeyHeader);
                System.out.println(masterKey);
                
                if (masterKeyHeader != null && masterKeyHeader.equals(masterKey)) {
                    // Master key is valid → allow request (optionally set a dummy authentication)
                    // We can set a special role if needed, but it's not required for authorization.
                    UsernamePasswordAuthenticationToken auth = 
                        new UsernamePasswordAuthenticationToken("master", null, 
                            List.of(new SimpleGrantedAuthority("ROLE_MASTER")));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    chain.doFilter(request, response);
                    return;
                } else {
                    // Invalid or missing master key
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing Master Key");
                    return;
                }
            }

            // 3. Otherwise, check JWT token for normal API endpoints
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                boolean valid = jwt.isAccessTokenValid(token);
                if (valid) {
                    String email = jwt.extractEmail(token);
                    String role = jwt.extractRole(token);
                    UsernamePasswordAuthenticationToken auth = 
                        new UsernamePasswordAuthenticationToken(email, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }

        } catch (Exception e) {
            logger.error("JWT Authentication Filter error: ", e);
        }

        chain.doFilter(request, response);
    }

    private boolean isPublic(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Authentication endpoints
        if (path.startsWith("/auth")) {
            return true;
        }

        // Public logger endpoints (log and error)
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
}