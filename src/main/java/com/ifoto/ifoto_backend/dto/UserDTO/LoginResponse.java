package com.ifoto.ifoto_backend.dto.UserDTO;

import java.util.Set;

public record LoginResponse(
        String accessToken,
        long expiresIn,
        String username,
        String email,
        String fullName,
        Set<String> roles,
        String profilePicture) {
}
