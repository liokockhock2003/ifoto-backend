// src/main/java/com/ifoto/ifoto_backend/controller/AuthController.java
package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.security.CookieUtil;
import com.ifoto.ifoto_backend.security.JwtUtil;
import com.ifoto.ifoto_backend.service.RefreshTokenService;
import com.ifoto.ifoto_backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody User user) {
        try {
            User savedUser = userService.register(user);
            return ResponseEntity.ok("User registered successfully: " + savedUser.getUsername());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
            HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()));

            String accessToken = jwtUtil.generateToken(authentication);
            String refreshToken = jwtUtil.generateRefreshToken(authentication);
            refreshTokenService.saveRefreshToken(
                    request.username(), refreshToken, jwtUtil.getRefreshExpirationMs());
            cookieUtil.setRefreshTokenCookie(response, refreshToken, jwtUtil.getRefreshExpirationMs());

            return ResponseEntity.ok(new LoginResponse(accessToken));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null
                || !jwtUtil.validateToken(refreshToken)
                || !jwtUtil.isRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or missing refresh token");
        }

        refreshTokenService.validateRefreshTokenInDb(refreshToken);
        String username = jwtUtil.getUsernameFromToken(refreshToken);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                username, null, Collections.emptyList());

        return ResponseEntity.ok(new LoginResponse(jwtUtil.generateToken(authentication)));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }

        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.ok("Logged out successfully");
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    record LoginRequest(String username, String password) {
    }

    record LoginResponse(String token) {
    }
}