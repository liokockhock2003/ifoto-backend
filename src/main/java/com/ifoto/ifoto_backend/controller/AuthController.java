// src/main/java/com/ifoto/ifoto_backend/controller/AuthController.java
package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

import com.ifoto.ifoto_backend.security.JwtUtil;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

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
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.username(),
                            request.password()));

            String jwt = jwtUtil.generateToken(authentication);
            return ResponseEntity.ok(new LoginResponse(jwt));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }
    }

    // Simple DTOs
    record LoginRequest(String username, String password) {
    }

    record LoginResponse(String token) {
    }
}