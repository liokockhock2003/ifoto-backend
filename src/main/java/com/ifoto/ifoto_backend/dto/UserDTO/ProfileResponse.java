package com.ifoto.ifoto_backend.dto.UserDTO;

public record ProfileResponse(
        String username,
        String email,
        String fullName,
        String phoneNumber,
        String position,
        boolean emailVerified) {
}
