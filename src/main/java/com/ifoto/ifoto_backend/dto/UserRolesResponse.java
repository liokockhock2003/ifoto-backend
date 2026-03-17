package com.ifoto.ifoto_backend.dto;

import java.util.Set;

public record UserRolesResponse(
        Long userId,
        String username,
        Set<String> roles) {
}
