package com.app.email.service;

import com.app.email.dto.EmailRequest;
import com.app.config.EmailProperties;
import com.app.core.exception.EmailSendException;

import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailService(JavaMailSender mailSender,
                            EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    // ✅ ASYNC ENTRY POINT
    @Override
    @Async
    public void send(EmailRequest request) {
        sendInternal(request);
    }

    // ✅ ASYNC ENTRY POINT
    @Override
    @Async
    public void send(String to, String subject, String body) {

        EmailRequest request = new EmailRequest();
        request.setTo(List.of(to));
        request.setSubject(subject);
        request.setBody(body);
        request.setHtml(false);

        sendInternal(request);
    }

    // ✅ ASYNC ENTRY POINT
    @Override
    @Async
    public void sendHtml(String to, String subject, String htmlBody) {

        EmailRequest request = new EmailRequest();
        request.setTo(List.of(to));
        request.setSubject(subject);
        request.setBody(htmlBody);
        request.setHtml(true);

        sendInternal(request);
    }

    // 🔒 SINGLE CORE METHOD (NOT ASYNC)
    private void sendInternal(EmailRequest request) {

        try {
            MimeMessage message = mailSender.createMimeMessage();

            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, "UTF-8");

            if (properties.getFrom() != null) {
                helper.setFrom(properties.getFrom());
            }

            if (properties.getReplyTo() != null) {
                helper.setReplyTo(properties.getReplyTo());
            }

            helper.setSubject(request.getSubject());
            helper.setText(request.getBody(), request.isHtml());
            helper.setTo(toArray(request.getTo()));

            if (request.getCc() != null && !request.getCc().isEmpty()) {
                helper.setCc(toArray(request.getCc()));
            }

            if (request.getBcc() != null && !request.getBcc().isEmpty()) {
                helper.setBcc(toArray(request.getBcc()));
            }

            mailSender.send(message);

        } catch (Exception ex) {
            throw new EmailSendException("SMTP send failed: " + ex.getMessage());
        }
    }

    private String[] toArray(List<String> list) {
        return list.toArray(new String[0]);
    }
}