package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.model.PasswordResetToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.PasswordResetTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import com.ifoto.ifoto_backend.service.MailService;
import com.ifoto.ifoto_backend.service.PasswordResetTokenService;
import com.ifoto.ifoto_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetTokenServiceTest {

    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private MailService mailService;

    @InjectMocks private PasswordResetTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tokenExpirationMs", 900000L);
        ReflectionTestUtils.setField(service, "resetUrlBase", "http://localhost/reset-password");

        user = User.builder().id(42L).email("alice@test.com").username("alice").build();
        lenient().when(passwordResetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── requestPasswordReset ─────────────────────────────────────────────────

    @Test
    void requestPasswordReset_nullEmail_returnsImmediately() {
        service.requestPasswordReset(null);

        verify(userRepository, never()).findByEmailAndIsActiveTrue(any());
    }

    @Test
    void requestPasswordReset_blankEmail_returnsImmediately() {
        service.requestPasswordReset("   ");

        verify(userRepository, never()).findByEmailAndIsActiveTrue(any());
    }

    @Test
    void requestPasswordReset_emailNotFound_noSideEffects() {
        when(userRepository.findByEmailAndIsActiveTrue("ghost@test.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("ghost@test.com");

        verify(passwordResetTokenRepository, never()).save(any());
        verify(mailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void requestPasswordReset_validEmail_marksOldTokensAndSavesNew() {
        when(userRepository.findByEmailAndIsActiveTrue("alice@test.com")).thenReturn(Optional.of(user));

        service.requestPasswordReset("alice@test.com");

        verify(passwordResetTokenRepository).markAllUnusedAsUsedByUserId(eq(42L), any(Instant.class));
        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void requestPasswordReset_validEmail_sendsResetEmailWithToken() {
        when(userRepository.findByEmailAndIsActiveTrue("alice@test.com")).thenReturn(Optional.of(user));
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);

        service.requestPasswordReset("alice@test.com");

        verify(mailService).sendPasswordResetEmail(eq("alice@test.com"), linkCaptor.capture());
        assertTrue(linkCaptor.getValue().contains("?token="));
    }

    @Test
    void requestPasswordReset_mailExceptionCaughtNotRethrown() {
        when(userRepository.findByEmailAndIsActiveTrue("alice@test.com")).thenReturn(Optional.of(user));
        doThrow(new MailException("smtp fail") {}).when(mailService).sendPasswordResetEmail(anyString(), anyString());

        assertDoesNotThrow(() -> service.requestPasswordReset("alice@test.com"));
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_nullToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword(null, "newPass123"));
        verify(passwordResetTokenRepository, never()).findByToken(any());
    }

    @Test
    void resetPassword_blankToken_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("  ", "newPass123"));
    }

    @Test
    void resetPassword_tokenNotFound_throwsIllegalArgument() {
        when(passwordResetTokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("bad", "newPass123"));
    }

    @Test
    void resetPassword_tokenAlreadyUsed_throwsIllegalArgument() {
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user).token("t1").used(true)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(passwordResetTokenRepository.findByToken("t1")).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("t1", "newPass123"));
    }

    @Test
    void resetPassword_tokenExpired_throwsIllegalArgument() {
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user).token("t2").used(false)
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(passwordResetTokenRepository.findByToken("t2")).thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("t2", "newPass123"));
    }

    @Test
    void resetPassword_validToken_callsUpdatePasswordAndMarksUsed() {
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user).token("t3").used(false)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(passwordResetTokenRepository.findByToken("t3")).thenReturn(Optional.of(token));

        service.resetPassword("t3", "newPass123");

        verify(userService).updatePassword(user, "newPass123");
        assertTrue(token.isUsed());
        assertNotNull(token.getUsedAt());
        verify(passwordResetTokenRepository).save(token);
        verify(passwordResetTokenRepository).markAllUnusedAsUsedByUserId(eq(42L), any(Instant.class));
    }
}
