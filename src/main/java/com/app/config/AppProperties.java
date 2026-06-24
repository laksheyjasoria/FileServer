package com.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String name;

    private String frontendUrl;

    private boolean redisEnabled;

    private boolean swaggerEnabled;

    public String getName() {
        return name;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public boolean isRedisEnabled() {
        return redisEnabled;
    }

    public boolean isSwaggerEnabled() {
        return swaggerEnabled;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public void setRedisEnabled(boolean redisEnabled) {
        this.redisEnabled = redisEnabled;
    }

    public void setSwaggerEnabled(boolean swaggerEnabled) {
        this.swaggerEnabled = swaggerEnabled;
    }
}