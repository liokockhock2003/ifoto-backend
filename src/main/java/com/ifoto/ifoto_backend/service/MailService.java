package com.ifoto.ifoto_backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.app-name:iFoto}")
    private String appName;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setFrom(from);
        message.setSubject(appName + " Password Reset Request");
        message.setText(buildResetPasswordBody(resetLink));
        mailSender.send(message);
    }

    private String buildResetPasswordBody(String resetLink) {
        return "We received a request to reset your password.\n\n"
                + "Use the link below to set a new password:\n"
                + resetLink
                + "\n\n"
                + "If you did not request this, please ignore this email.";
    }
}
