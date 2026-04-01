package com.ifoto.ifoto_backend.dto.UserDTO;

import java.util.Set;

public record UserUpdateResponse(
        Long userId,
        String username,
        String fullName,
        Set<String> roles,
        String activeRole,
        boolean locked) {
}
