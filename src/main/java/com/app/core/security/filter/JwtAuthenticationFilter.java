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

        // 1. Public endpoints – skip authentication (only truly public paths)
        if (isPublic(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 2. Handle Master Key / Admin special endpoints
        if (path.startsWith("/logger/") || path.startsWith("/admin/")) {
            if (handleMasterKeyOrAdmin(request, response, chain)) {
                return;
            }
            return;
        }

        // 3. Regular JWT authentication for all other APIs (including /share/**)
        handleRegularJwt(request);
        chain.doFilter(request, response);
    }

    // ---------------------- Helper Methods ----------------------

    private boolean isPublic(String path) {
        // Explicitly public auth endpoints
        if (path.equals("/auth/login") || path.equals("/auth/register") || path.equals("/auth/google")
                || path.equals("/auth/forgot-password") || path.equals("/auth/reset-password")) {
            return true;
        }
        // Public logger endpoints
        if (path.equals("/logger/log") || path.equals("/logger/error")) {
            return true;
        }
        // Static resources – do NOT include "/share/" here!
        if (path.startsWith("/js/") || path.startsWith("/js2/") || path.startsWith("/css/")
                || path.startsWith("/images/") || path.startsWith("/assets/") || path.equals("/favicon.ico")
                || path.equals("/") || path.equals("/index.html")) {
            return true;
        }
        return path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".json")
                || path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".gif")
                || path.endsWith(".svg") || path.endsWith(".ico") || path.endsWith(".woff") || path.endsWith(".woff2")
                || path.endsWith(".ttf");
    }

    private boolean handleMasterKeyOrAdmin(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        boolean isAdminDelete = path.startsWith("/admin/") && "DELETE".equalsIgnoreCase(method);
        if (!isAdminDelete) {
            String masterKeyHeader = request.getHeader("X-Master-Key");
            if (masterKeyHeader != null && masterKeyHeader.equals(masterKey)) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("master", null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(auth);
                chain.doFilter(request, response);
                return true;
            }
        }

        if (path.startsWith("/admin/")) {
            return handleAdminJwt(request, response, chain);
        }

        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing Master Key");
        return false;
    }

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