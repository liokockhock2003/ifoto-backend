package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.model.RefreshToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.RefreshTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import com.ifoto.ifoto_backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private RefreshTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).username("alice").build();
    }

    // ── validateRefreshTokenInDb ─────────────────────────────────────────────

    @Test
    void validateRefreshTokenInDb_tokenNotFound_throwsUnauthorized() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validateRefreshTokenInDb("missing"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void validateRefreshTokenInDb_revoked_throwsUnauthorized() {
        RefreshToken token = RefreshToken.builder()
                .token("t1").revoked(true)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(refreshTokenRepository.findByToken("t1")).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validateRefreshTokenInDb("t1"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void validateRefreshTokenInDb_expired_throwsUnauthorized() {
        RefreshToken token = RefreshToken.builder()
                .token("t2").revoked(false)
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(refreshTokenRepository.findByToken("t2")).thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.validateRefreshTokenInDb("t2"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void validateRefreshTokenInDb_validToken_doesNotThrow() {
        RefreshToken token = RefreshToken.builder()
                .token("t3").revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(refreshTokenRepository.findByToken("t3")).thenReturn(Optional.of(token));

        assertDoesNotThrow(() -> service.validateRefreshTokenInDb("t3"));
    }

    // ── saveRefreshToken ─────────────────────────────────────────────────────

    @Test
    void saveRefreshToken_userNotFound_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.saveRefreshToken("ghost", "tok", 3600000L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void saveRefreshToken_savesTokenWithCorrectFields() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        service.saveRefreshToken("alice", "tok123", 3600000L);

        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertEquals("tok123", saved.getToken());
        assertFalse(saved.isRevoked());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
        assertEquals(user, saved.getUser());
    }

    // ── revokeToken ──────────────────────────────────────────────────────────

    @Test
    void revokeToken_tokenFound_setsRevokedAndSaves() {
        RefreshToken token = RefreshToken.builder().token("t1").revoked(false).build();
        when(refreshTokenRepository.findByToken("t1")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeToken("t1");

        verify(refreshTokenRepository).save(argThat(RefreshToken::isRevoked));
    }

    @Test
    void revokeToken_tokenNotFound_noSave() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());

        service.revokeToken("missing");

        verify(refreshTokenRepository, never()).save(any());
    }

    // ── revokeAllTokensForUser ───────────────────────────────────────────────

    @Test
    void revokeAllTokensForUser_userNotFound_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.revokeAllTokensForUser("ghost"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void revokeAllTokensForUser_delegatesToRepository() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        service.revokeAllTokensForUser("alice");

        verify(refreshTokenRepository).revokeAllByUserId(7L);
    }
}
