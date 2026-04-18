// src/main/java/com/ifoto/ifoto_backend/controller/AuthController.java
package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.UserDTO.ForgotPasswordRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.LoginRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.LoginResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.RegisterRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.RegisterResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.ResetPasswordRequest;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.security.CookieUtil;
import com.ifoto.ifoto_backend.security.JwtUtil;
import com.ifoto.ifoto_backend.service.EmailVerificationTokenService;
import com.ifoto.ifoto_backend.service.PasswordResetTokenService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final CookieUtil cookieUtil;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final EmailVerificationTokenService emailVerificationTokenService;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(req.password())
                .fullName(req.fullName())
                .phoneNumber(req.phoneNumber())
                .profilePictureUrl(req.profilePicture())
                .build();

        User savedUser = userService.register(user);
        emailVerificationTokenService.sendVerificationEmail(savedUser);

        var roles = userService.getRoleNamesByUsername(savedUser.getUsername());
        var resp = new RegisterResponse(
                savedUser.getId(),
                savedUser.getUsername(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                roles,
                savedUser.getCreatedAt());

        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/users/{id}")
                .buildAndExpand(savedUser.getId())
                .toUri();

        return ResponseEntity.created(location).body(resp);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request,
            HttpServletResponse response) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()));

        User user = userService.getByUsername(request.username());
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Email not verified. Please check your inbox.");
        }

        String accessToken = jwtUtil.generateToken(authentication);
        String refreshToken = jwtUtil.generateRefreshToken(authentication);
        refreshTokenService.saveRefreshToken(
                request.username(), refreshToken, jwtUtil.getRefreshExpirationMs());
        cookieUtil.setRefreshTokenCookie(response, refreshToken, jwtUtil.getRefreshExpirationMs());
        Set<String> roles = userService.getRoleNamesByUsername(request.username());

        return ResponseEntity.ok(new LoginResponse(
                accessToken,
                jwtUtil.getExpirationMs(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roles,
                user.getProfilePictureUrl()));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<LoginResponse> refresh(
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

        String newAccessToken = jwtUtil.generateToken(authentication);
        Set<String> roles = userService.getRoleNamesByUsername(username);
        User user = userService.getByUsername(username);

        return ResponseEntity.ok(new LoginResponse(
                newAccessToken,
                jwtUtil.getExpirationMs(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roles,
                user.getProfilePictureUrl()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<String> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null) {
            refreshTokenService.revokeToken(refreshToken);
        }

        cookieUtil.clearRefreshTokenCookie(response);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/auth/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetTokenService.requestPasswordReset(request.email());
        return ResponseEntity.ok("If an account with that email exists, a reset link has been sent.");
    }

    @PostMapping("/auth/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetTokenService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok("Password reset successful");
    }

    @GetMapping("/auth/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam String token) {
        emailVerificationTokenService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully. You can now log in.");
    }
}
