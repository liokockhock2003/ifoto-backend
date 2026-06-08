package com.ifoto.ifoto_backend.dto.UserDTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BankDetailRequest(
        @NotBlank(message = "Bank name is required")
        @Size(max = 100)
        String bankName,

        @NotBlank(message = "Account number is required")
        @Size(max = 50)
        String accountNo,

        String signature
) {}
