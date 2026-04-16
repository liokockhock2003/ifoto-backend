package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.EmailVerificationToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.EmailVerificationTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationTokenService {

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Value("${app.email-verification.token-expiration-ms:86400000}")
    private long tokenExpirationMs;

    @Value("${app.email-verification.verify-url-base:http://localhost:5173/verify-email}")
    private String verifyUrlBase;

    @Transactional
    public void sendVerificationEmail(User user) {
        // Invalidate any previous unused tokens for this user
        emailVerificationTokenRepository.markAllUnusedAsUsedByUserId(user.getId(), Instant.now());

        String token = UUID.randomUUID().toString();
        EmailVerificationToken verificationToken = EmailVerificationToken.builder()
                .user(user)
                .token(token)
                .expiresAt(Instant.now().plusMillis(tokenExpirationMs))
                .used(false)
                .build();

        emailVerificationTokenRepository.save(verificationToken);

        try {
            mailService.sendVerificationEmail(user.getEmail(), buildVerificationLink(token));
        } catch (MailException ex) {
            log.error("Verification email delivery failed for userId={} email={}", user.getId(), user.getEmail(), ex);
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Verification token is required");
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid verification token"));

        if (verificationToken.isUsed()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }
        if (verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verificationToken.setUsed(true);
        verificationToken.setUsedAt(Instant.now());
        emailVerificationTokenRepository.save(verificationToken);
    }

    private String buildVerificationLink(String token) {
        return UriComponentsBuilder
                .fromUriString(verifyUrlBase)
                .queryParam("token", token)
                .build()
                .encode()
                .toUriString();
    }
}
