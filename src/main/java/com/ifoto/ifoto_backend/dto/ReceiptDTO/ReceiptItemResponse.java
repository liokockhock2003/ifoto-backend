package com.ifoto.ifoto_backend.dto.ReceiptDTO;

public record ReceiptItemResponse(
        String equipmentType,
        String brand,
        String model,
        String serialNumber,
        String pricingCategory,
        Long rateApplied,
        Long latePenalty,
        Long itemTotal
) {}
