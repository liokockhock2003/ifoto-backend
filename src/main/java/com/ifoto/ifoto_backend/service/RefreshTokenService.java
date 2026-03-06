package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.RefreshToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.RefreshTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    // ── Save on login ──────────────────────────────────────────────────────────

    @Transactional
    public void saveRefreshToken(String username, String token, long expirationMs) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(token)
                .expiresAt(Instant.now().plusMillis(expirationMs))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
    }

    // ── Validate on refresh ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void validateRefreshTokenInDb(String token) {
        RefreshToken stored = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        if (stored.isRevoked()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token revoked");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
        }
    }

    // ── Revoke on logout ───────────────────────────────────────────────────────

    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    // ── Revoke ALL tokens for user (logout all devices) ───────────────────────

    @Transactional
    public void revokeAllTokensForUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        refreshTokenRepository.revokeAllByUserId(user.getId());
    }
}