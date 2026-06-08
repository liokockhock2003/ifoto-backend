package com.ifoto.ifoto_backend.dto.UserDTO;

public record BankDetailResponse(
        String bankName,
        String accountNo,
        String accountName,
        String username,
        String signature
) {}
