package com.ifoto.ifoto_backend.dto;

import java.util.Set;

public record UserListItemResponse(
        Long id,
        String username,
        String email,
        String fullName,
        boolean isActive,
        boolean isLocked,
        Set<String> roles) {
}
