package com.ifoto.ifoto_backend.unit.security;

import com.ifoto.ifoto_backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    @InjectMocks
    private JwtUtil jwtUtil;

    private static final String SECRET = "test-secret-key-that-is-at-least-32-chars-long!";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtUtil, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", 900000L);
        ReflectionTestUtils.setField(jwtUtil, "jwtRefreshExpirationMs", 604800000L);
    }

    @Test
    void generateToken_validAuthentication_returnsNonEmptyToken() {
        Authentication auth = mockAuth("alice");
        String token = jwtUtil.generateToken(auth);
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void validateToken_freshAccessToken_returnsTrue() {
        String token = jwtUtil.generateToken(mockAuth("alice"));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtUtil, "jwtExpirationMs", -1000L);
        String token = jwtUtil.generateToken(mockAuth("alice"));
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_tamperedSignature_returnsFalse() {
        String token = jwtUtil.generateToken(mockAuth("alice"));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    void validateToken_randomString_returnsFalse() {
        assertFalse(jwtUtil.validateToken("not.a.jwt"));
    }

    @Test
    void getUsernameFromToken_returnsCorrectSubject() {
        String token = jwtUtil.generateToken(mockAuth("bob"));
        assertEquals("bob", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    void isRefreshToken_accessToken_returnsFalse() {
        String token = jwtUtil.generateToken(mockAuth("alice"));
        assertFalse(jwtUtil.isRefreshToken(token));
    }

    @Test
    void isRefreshToken_refreshToken_returnsTrue() {
        String token = jwtUtil.generateRefreshToken(mockAuth("alice"));
        assertTrue(jwtUtil.isRefreshToken(token));
    }

    @Test
    void isRefreshToken_invalidToken_returnsFalse() {
        assertFalse(jwtUtil.isRefreshToken("garbage"));
    }

    private Authentication mockAuth(String username) {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(username);
        return auth;
    }
}
