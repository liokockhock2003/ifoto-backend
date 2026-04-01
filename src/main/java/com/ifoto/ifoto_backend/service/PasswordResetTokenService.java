package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.PasswordResetToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.PasswordResetTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
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
            mailService.sendPasswordResetEmail(user.getEmail(), buildResetLink(token));
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Reset token is required");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
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
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return resetUrlBase + "?token=" + encodedToken;
    }
}
