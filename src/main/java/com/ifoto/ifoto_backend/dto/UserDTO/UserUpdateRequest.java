package com.ifoto.ifoto_backend.dto.UserDTO;

import jakarta.validation.constraints.NotBlank;

import java.util.Set;

public record UserUpdateRequest(
        Set<@NotBlank(message = "Role name must not be blank") String> roles,
        Boolean locked) {
}
