package com.ifoto.ifoto_backend.dto.PaymentDTO;

public record PaymentResponse(
        Long id,
        String paymentType,
        String status,
        Long amount,
        String billUrl,
        String billId,
        String message
) {}
