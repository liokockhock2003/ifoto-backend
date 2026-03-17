package com.ifoto.ifoto_backend.dto;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Response returned after successful user registration.
 */
public record RegisterResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Set<String> roles,
        LocalDateTime createdAt
) {
}
