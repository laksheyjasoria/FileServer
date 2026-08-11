package com.app.core.security.masterkey;

import java.io.IOException;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.app.config.MasterKeyProperties;
import com.app.core.exception.UnauthorizedException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class MasterKeyFilter implements Filter {

	private final MasterKeyProperties props;

	public MasterKeyFilter(MasterKeyProperties props) {
		this.props = props;
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {

		if (SecurityContextHolder.getContext().getAuthentication() != null) {
			chain.doFilter(req, res);
			return;
		}

		HttpServletRequest request = (HttpServletRequest) req;

		if (requiresMasterKey(request)) {

			String key = request.getHeader("X-MASTER-KEY");

			if (key == null || !key.equals(props.getKey())) {
				throw new UnauthorizedException("Invalid master key");
			}
		}

		chain.doFilter(req, res);
	}

	private boolean requiresMasterKey(HttpServletRequest request) {

		String path = request.getRequestURI();
		String method = request.getMethod();

		// 🔐 Logger Admin APIs
		if (path.startsWith("/logger")) {
			return method.equals("POST") && path.contains("/create") || method.equals("PUT") || method.equals("DELETE");
		}

		// 🔐 Email APIs
		if (path.startsWith("/email")) {
			return true;
		}

		// 🔐 Admin API - list/manage master drives
		if (path.startsWith("/admin")) {
			return true;
		}

		return false;
	}
}