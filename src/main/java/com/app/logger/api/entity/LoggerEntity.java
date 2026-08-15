package com.app.logger.api.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class LoggerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String name;

    private boolean infoEnabled = true;
    private boolean warnEnabled = true;
    
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean debugEnabled = false;

    // Getters and Setters
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isInfoEnabled() {
        return infoEnabled;
    }

    public boolean isWarnEnabled() {
        return warnEnabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInfoEnabled(boolean infoEnabled) {
        this.infoEnabled = infoEnabled;
    }

    public void setWarnEnabled(boolean warnEnabled) {
        this.warnEnabled = warnEnabled;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }
}