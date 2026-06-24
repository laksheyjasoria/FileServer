package com.app.email.dto;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public class EmailRequest {

	@NotEmpty(message = "At least one recipient is required")
	private List<@Email(message = "Invalid email in TO list") String> to;

	private List<@Email(message = "Invalid email in CC list") String> cc;

	private List<@Email(message = "Invalid email in BCC list") String> bcc;

	@NotBlank(message = "Subject is required")
	private String subject;

	@NotBlank(message = "Body is required")
	private String body;

	private boolean html;

	public List<String> getTo() {
		return to;
	}

	public void setTo(List<String> to) {
		this.to = to;
	}

	public List<String> getCc() {
		return cc;
	}

	public void setCc(List<String> cc) {
		this.cc = cc;
	}

	public List<String> getBcc() {
		return bcc;
	}

	public void setBcc(List<String> bcc) {
		this.bcc = bcc;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getBody() {
		return body;
	}

	public void setBody(String body) {
		this.body = body;
	}

	public boolean isHtml() {
		return html;
	}

	public void setHtml(boolean html) {
		this.html = html;
	}

}