package com.ifoto.ifoto_backend.dto.UserDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 255) String newPassword) {
}
