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

	public void setName(String name) {
		this.name = name;
	}

	public void setInfoEnabled(boolean infoEnabled) {
		this.infoEnabled = infoEnabled;
	}

	public void setWarnEnabled(boolean warnEnabled) {
		this.warnEnabled = warnEnabled;
	}
}