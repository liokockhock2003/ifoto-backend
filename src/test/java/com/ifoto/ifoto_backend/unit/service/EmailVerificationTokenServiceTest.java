package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.exception.TokenException;
import com.ifoto.ifoto_backend.model.EmailVerificationToken;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.EmailVerificationTokenRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import com.ifoto.ifoto_backend.service.EmailVerificationTokenService;
import com.ifoto.ifoto_backend.service.MailService;
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
class EmailVerificationTokenServiceTest {

    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;

    @InjectMocks private EmailVerificationTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "tokenExpirationMs", 86400000L);
        ReflectionTestUtils.setField(service, "verifyUrlBase", "http://localhost/verify-email");

        user = User.builder().id(1L).email("alice@test.com").username("alice").build();

        lenient().when(emailVerificationTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── sendVerificationEmail ────────────────────────────────────────────────

    @Test
    void sendVerificationEmail_marksOldTokensUsedBeforeSavingNew() {
        service.sendVerificationEmail(user);

        verify(emailVerificationTokenRepository).markAllUnusedAsUsedByUserId(eq(1L), any(Instant.class));
        verify(emailVerificationTokenRepository).save(any(EmailVerificationToken.class));
    }

    @Test
    void sendVerificationEmail_savesTokenWithCorrectFields() {
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);

        service.sendVerificationEmail(user);

        verify(emailVerificationTokenRepository).save(captor.capture());
        EmailVerificationToken saved = captor.getValue();
        assertNotNull(saved.getToken());
        assertFalse(saved.getToken().isBlank());
        assertFalse(saved.isUsed());
        assertEquals(user, saved.getUser());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void sendVerificationEmail_sendsEmailWithLinkContainingToken() throws Exception {
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);

        service.sendVerificationEmail(user);

        verify(mailService).sendVerificationEmail(eq("alice@test.com"), linkCaptor.capture());
        assertTrue(linkCaptor.getValue().contains("?token="));
    }

    @Test
    void sendVerificationEmail_mailExceptionCaughtNotRethrown() {
        doThrow(new MailException("smtp fail") {}).when(mailService).sendVerificationEmail(anyString(), anyString());

        assertDoesNotThrow(() -> service.sendVerificationEmail(user));
    }

    // ── verifyEmail ──────────────────────────────────────────────────────────

    @Test
    void verifyEmail_nullToken_throwsMissing() {
        TokenException ex = assertThrows(TokenException.class, () -> service.verifyEmail(null));
        assertEquals(TokenException.Reason.MISSING, ex.getReason());
    }

    @Test
    void verifyEmail_blankToken_throwsMissing() {
        TokenException ex = assertThrows(TokenException.class, () -> service.verifyEmail("   "));
        assertEquals(TokenException.Reason.MISSING, ex.getReason());
    }

    @Test
    void verifyEmail_tokenNotFound_throwsInvalid() {
        when(emailVerificationTokenRepository.findByToken("bad")).thenReturn(Optional.empty());

        TokenException ex = assertThrows(TokenException.class, () -> service.verifyEmail("bad"));
        assertEquals(TokenException.Reason.INVALID, ex.getReason());
    }

    @Test
    void verifyEmail_tokenAlreadyUsed_throwsAlreadyUsed() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user).token("t1").used(true)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(emailVerificationTokenRepository.findByToken("t1")).thenReturn(Optional.of(token));

        TokenException ex = assertThrows(TokenException.class, () -> service.verifyEmail("t1"));
        assertEquals(TokenException.Reason.ALREADY_USED, ex.getReason());
    }

    @Test
    void verifyEmail_tokenExpired_throwsExpired() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user).token("t2").used(false)
                .expiresAt(Instant.now().minusSeconds(1)).build();
        when(emailVerificationTokenRepository.findByToken("t2")).thenReturn(Optional.of(token));

        TokenException ex = assertThrows(TokenException.class, () -> service.verifyEmail("t2"));
        assertEquals(TokenException.Reason.EXPIRED, ex.getReason());
    }

    @Test
    void verifyEmail_validToken_setsEmailVerifiedAndMarksUsed() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .user(user).token("t3").used(false)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(emailVerificationTokenRepository.findByToken("t3")).thenReturn(Optional.of(token));

        service.verifyEmail("t3");

        assertTrue(user.isEmailVerified());
        verify(userRepository).save(user);
        assertTrue(token.isUsed());
        assertNotNull(token.getUsedAt());
        verify(emailVerificationTokenRepository).save(token);
    }
}
