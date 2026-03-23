package com.ifoto.ifoto_backend.dto.UserDTO;

import jakarta.validation.constraints.NotBlank;

public record ActiveRoleSwitchRequest(
        @NotBlank(message = "roleName is required") String roleName) {
}
