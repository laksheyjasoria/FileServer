package com.app.core.security.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.app.core.security.jwt.JwtService;
import com.app.identity.entity.User;
import com.app.identity.enums.UserStatus;
import com.app.identity.repository.UserRepository;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    // ================================
    // 1. ACCESS LEVEL ENUM (inner)
    // ================================
    private enum AccessLevel {
        PUBLIC,          // No auth required
        USER,            // Any valid JWT (active account)
        ADMIN,           // Admin JWT only (NO Master Key)
        MASTER_KEY,      // Master Key only
        MASTER_OR_ADMIN  // Master Key OR Admin JWT
    }

    // ================================
    // 2. ENDPOINT REGISTRY (inner)
    // ================================
    private static class EndpointRule {
        final String pattern;
        final AccessLevel level;
        EndpointRule(String pattern, AccessLevel level) {
            this.pattern = pattern;
            this.level = level;
        }
    }

    private final List<EndpointRule> rules = new ArrayList<>();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // ================================
    // 3. DEPENDENCIES
    // ================================
    private final JwtService jwt;
    private final String masterKey;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwt, String masterKey, UserRepository userRepository) {
        this.jwt = jwt;
        this.masterKey = masterKey;
        this.userRepository = userRepository;
    }

    // ================================
    // 4. INIT – POPULATE REGISTRY (EXACTLY MATCHES OLD isPublic)
    // ================================
    @PostConstruct
    public void initRegistry() {
        // ---- PUBLIC: exactly what the old isPublic() returned true for ----
        addRule("/auth/login", AccessLevel.PUBLIC);
        addRule("/auth/register", AccessLevel.PUBLIC);
        addRule("/auth/google", AccessLevel.PUBLIC);
        addRule("/auth/forgot-password", AccessLevel.PUBLIC);
        addRule("/auth/reset-password", AccessLevel.PUBLIC);
        addRule("/logger/log", AccessLevel.PUBLIC);
        addRule("/logger/error", AccessLevel.PUBLIC);
        addRule("/js/**", AccessLevel.PUBLIC);
        addRule("/js2/**", AccessLevel.PUBLIC);
        addRule("/css/**", AccessLevel.PUBLIC);
        addRule("/images/**", AccessLevel.PUBLIC);
        addRule("/assets/**", AccessLevel.PUBLIC);
        addRule("/favicon.ico", AccessLevel.PUBLIC);
        addRule("/", AccessLevel.PUBLIC);
        addRule("/index.html", AccessLevel.PUBLIC);
        // Also include all static file extensions
        addRule("/*.html", AccessLevel.PUBLIC);
        addRule("/*.css", AccessLevel.PUBLIC);
        addRule("/*.js", AccessLevel.PUBLIC);
        addRule("/*.json", AccessLevel.PUBLIC);
        addRule("/*.png", AccessLevel.PUBLIC);
        addRule("/*.jpg", AccessLevel.PUBLIC);
        addRule("/*.jpeg", AccessLevel.PUBLIC);
        addRule("/*.gif", AccessLevel.PUBLIC);
        addRule("/*.svg", AccessLevel.PUBLIC);
        addRule("/*.ico", AccessLevel.PUBLIC);
        addRule("/*.woff", AccessLevel.PUBLIC);
        addRule("/*.woff2", AccessLevel.PUBLIC);
        addRule("/*.ttf", AccessLevel.PUBLIC);

        // ---- ADMIN (strictly Admin JWT only, no Master Key) ----
        addRule("/api/admin/**", AccessLevel.ADMIN);

        // ---- MASTER_OR_ADMIN (Master Key OR Admin JWT) ----
        addRule("/admin/**", AccessLevel.MASTER_OR_ADMIN);
        addRule("/logger/**", AccessLevel.MASTER_OR_ADMIN);
        addRule("/master/**", AccessLevel.MASTER_OR_ADMIN);

        // ---- NOTE: /share/** is NOT listed here – it defaults to USER.
        // This exactly matches the old filter's behavior.
    }

    private void addRule(String pattern, AccessLevel level) {
        rules.add(new EndpointRule(pattern, level));
    }

    private AccessLevel getAccessLevel(String path) {
        for (EndpointRule rule : rules) {
            if (pathMatcher.match(rule.pattern, path)) {
                return rule.level;
            }
        }
        return AccessLevel.USER; // default
    }

    // ================================
    // 5. MAIN FILTER LOGIC
    // ================================
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        AccessLevel level = getAccessLevel(path);

        switch (level) {
            case PUBLIC:
                chain.doFilter(request, response);
                return;

            case ADMIN:
                if (handleAdminJwt(request, response, chain)) return;
                sendUnauthorized(response, "Invalid or missing Admin token");
                return;

            case MASTER_KEY:
                if (handleMasterKey(request, response, chain)) return;
                sendUnauthorized(response, "Invalid or missing Master Key");
                return;

            case MASTER_OR_ADMIN:
                if (handleMasterKey(request, response, chain)) return;
                if (handleAdminJwt(request, response, chain)) return;
                sendUnauthorized(response, "Invalid Master Key or Admin token");
                return;

            case USER:
            default:
                if (!handleRegularJwt(request, response)) {
                    // No token – let Spring Security handle it
                }
                chain.doFilter(request, response);
        }
    }

    // ================================
    // 6. HANDLER METHODS
    // ================================

    private boolean handleMasterKey(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = request.getHeader("X-Master-Key");
        if (header != null && header.equals(masterKey)) {
            setAuthentication("master", "ROLE_ADMIN");
            chain.doFilter(request, response);
            return true;
        }
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
                    if (!isUserActive(email, response)) return false;
                    setAuthentication(email, "ROLE_ADMIN");
                    chain.doFilter(request, response);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleRegularJwt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                if (jwt.isAccessTokenValid(token)) {
                    String email = jwt.extractEmail(token);
                    if (!isUserActive(email, response)) return false;
                    String role = jwt.extractRole(token);
                    setAuthentication(email, "ROLE_" + role);
                    return true;
                }
            } catch (io.jsonwebtoken.ExpiredJwtException e) {
                logger.debug("Expired JWT token: {}", e.getMessage());
            } catch (Exception e) {
                logger.debug("Invalid JWT token: {}", e.getMessage());
            }
        }
        return true;
    }

    // ================================
    // 7. HELPERS
    // ================================

    private boolean isUserActive(String email, HttpServletResponse response) throws IOException {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            logger.warn("User {} not active or not found – rejecting request", email);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                        "success": false,
                        "errorCode": "ACCOUNT_DEACTIVATED",
                        "message": "Your account has been deactivated. Please contact the administrator."
                    }
                    """);
            return false;
        }
        return true;
    }

    private void setAuthentication(String principal, String authority) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority(authority)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("""
                {
                    "success": false,
                    "message": "%s"
                }
                """.formatted(message));
    }
}