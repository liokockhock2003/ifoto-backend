package com.ifoto.ifoto_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UserRolesRequest(
        @NotNull(message = "Roles list is required") Set<@NotBlank(message = "Role name must not be blank") String> roles) {
}
