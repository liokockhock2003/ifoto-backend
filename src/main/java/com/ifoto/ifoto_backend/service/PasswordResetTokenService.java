package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.PasswordResetToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.PasswordResetTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MailService mailService;

    @Value("${app.password-reset.token-expiration-ms:900000}")
    private long tokenExpirationMs;

    @Value("${app.password-reset.reset-url-base:http://localhost:5173/reset-password}")
    private String resetUrlBase;

    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        userRepository.findByEmailAndIsActiveTrue(email.trim()).ifPresent(user -> {
            passwordResetTokenRepository.markAllUnusedAsUsedByUserId(user.getId(), Instant.now());

            String token = UUID.randomUUID().toString();
            PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(Instant.now().plusMillis(tokenExpirationMs))
                    .used(false)
                    .build();

            passwordResetTokenRepository.save(passwordResetToken);
            try {
                mailService.sendPasswordResetEmail(user.getEmail(), buildResetLink(token));
            } catch (Exception ex) {
                log.warn("Failed to send password reset email for userId={}", user.getId(), ex);
            }
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid reset token"));

        if (passwordResetToken.isUsed()) {
            throw new IllegalArgumentException("Reset token has already been used");
        }
        if (passwordResetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        User user = passwordResetToken.getUser();
        userService.updatePassword(user, newPassword);

        passwordResetToken.setUsed(true);
        passwordResetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(passwordResetToken);

        // Invalidate any other still-usable reset tokens for this user.
        passwordResetTokenRepository.markAllUnusedAsUsedByUserId(user.getId(), Instant.now());
    }

    private String buildResetLink(String token) {
        return UriComponentsBuilder
                .fromUriString(resetUrlBase)
                .queryParam("token", token)
                .build()
                .toUriString();
    }
}
