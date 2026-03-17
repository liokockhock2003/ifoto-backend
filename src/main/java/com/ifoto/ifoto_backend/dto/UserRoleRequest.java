package com.ifoto.ifoto_backend.dto;

import jakarta.validation.constraints.NotBlank;

public record UserRoleRequest(
        @NotBlank(message = "Role name is required")
        String roleName) {
}
