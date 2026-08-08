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

//	@Override
//	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
//			throws ServletException, IOException {
//
//		try {
//
//			// 🔓 Skip public endpoints
//			if (isPublic(request)) {
//				chain.doFilter(request, response);
//				return;
//			}
//
//			String header = request.getHeader("Authorization");
//
//			if (header != null && header.startsWith("Bearer ")) {
//
//				String token = header.substring(7);
//
//				if (jwt.isAccessTokenValid(token)) {
//
//					String email = jwt.extractEmail(token);
//					String role = jwt.extractRole(token);
//
//					UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(email, null,
//							List.of(new SimpleGrantedAuthority("ROLE_" + role)));
//
//					SecurityContextHolder.getContext().setAuthentication(auth);
//				}
//			}
//
//		} catch (Exception ignored) {
//			// ❌ Never break request flow
//		}
//
//		chain.doFilter(request, response);
//	}

	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain chain)
	        throws ServletException, IOException {

	    try {

	        System.out.println("=================================");
	        System.out.println("PATH = " + request.getRequestURI());

	        // Skip public endpoints
	        if (isPublic(request)) {
	            System.out.println("PUBLIC ENDPOINT");
	            chain.doFilter(request, response);
	            return;
	        }

	        String header = request.getHeader("Authorization");

	        System.out.println("HEADER = " + header);

	        if (header != null && header.startsWith("Bearer ")) {

	            String token = header.substring(7);

	            System.out.println("TOKEN = " + token);

	            boolean valid = jwt.isAccessTokenValid(token);

	            System.out.println("TOKEN VALID = " + valid);

	            if (valid) {

	                String email = jwt.extractEmail(token);
	                String role = jwt.extractRole(token);

	                System.out.println("EMAIL = " + email);
	                System.out.println("ROLE = " + role);

	                UsernamePasswordAuthenticationToken auth =
	                        new UsernamePasswordAuthenticationToken(
	                                email,
	                                null,
	                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
	                        );

	                SecurityContextHolder.getContext().setAuthentication(auth);

	                System.out.println("AUTHENTICATION SET SUCCESSFULLY");
	            } else {
	                System.out.println("TOKEN VALIDATION FAILED");
	            }
	        } else {
	            System.out.println("NO AUTH HEADER FOUND");
	        }

	    } catch (Exception e) {

	        System.out.println("JWT FILTER ERROR");
	        e.printStackTrace();

	    }

	    chain.doFilter(request, response);
	}
	private boolean isPublic(HttpServletRequest request) {

		String path = request.getRequestURI();

		return path.startsWith("/auth") || path.startsWith("/logger/log") || path.startsWith("/logger/error");
	}
}