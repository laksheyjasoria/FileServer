package com.app.email.service;

import com.app.email.dto.EmailRequest;

public interface EmailService {
	void send(EmailRequest request);

	void send(String to, String subject, String body);
	
	void sendHtml(String to, String subject, String htmlBody);
}