package com.ifoto.ifoto_backend.dto.UserDTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name cannot exceed 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone number cannot exceed 20 characters")
        String phoneNumber,

        @Size(max = 100, message = "Position cannot exceed 100 characters")
        String position
) {}
