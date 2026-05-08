package com.ifoto.ifoto_backend.dto.ReceiptDTO;

import java.time.LocalDateTime;

public record ReceiptSummaryResponse(
        String receiptNumber,
        LocalDateTime issuedAt,
        String rentalNumber,
        Long totalAmount,
        String paymentType
) {}
