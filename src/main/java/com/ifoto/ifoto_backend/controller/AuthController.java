// src/main/java/com/ifoto/ifoto_backend/controller/AuthController.java
package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.UserDTO.LoginRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.LoginResponse;
import com.ifoto.ifoto_backend.dto.UserDTO.RegisterRequest;
import com.ifoto.ifoto_backend.dto.UserDTO.RegisterResponse;
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

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            // Map DTO -> entity. The service will hash the password before saving.
            User user = User.builder()
                    .username(req.username())
                    .email(req.email())
                    .passwordHash(req.password())
                    .fullName(req.fullName())
                    .phoneNumber(req.phoneNumber())
                    .profilePictureUrl(req.profilePictureUrl())
                    .build();

            User savedUser = userService.register(user);

            // Build response DTO
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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Registration failed");
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

            User user = userService.getByUsername(request.username());
            Set<String> roles = userService.getRoleNamesByUsername(request.username());

            return ResponseEntity.ok(new LoginResponse(
                    accessToken,
                    jwtUtil.getExpirationMs(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getFullName(),
                    roles));
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

        String newAccessToken = jwtUtil.generateToken(authentication);
        Set<String> roles = userService.getRoleNamesByUsername(username);
        User user = userService.getByUsername(username);

        return ResponseEntity.ok(new LoginResponse(
                newAccessToken,
                jwtUtil.getExpirationMs(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roles));
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
}