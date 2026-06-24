package com.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public class SecurityProperties {

	private String secret;
	private long accessTokenValidity;
	private long resetTokenValidity;
	
	private boolean redisEnabled;

	public boolean isRedisEnabled() {
	    return redisEnabled;
	}

	public void setRedisEnabled(boolean redisEnabled) {
	    this.redisEnabled = redisEnabled;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public long getAccessTokenValidity() {
		return accessTokenValidity;
	}

	public void setAccessTokenValidity(long accessTokenValidity) {
		this.accessTokenValidity = accessTokenValidity;
	}

	public long getResetTokenValidity() {
		return resetTokenValidity;
	}

	public void setResetTokenValidity(long resetTokenValidity) {
		this.resetTokenValidity = resetTokenValidity;
	}
}