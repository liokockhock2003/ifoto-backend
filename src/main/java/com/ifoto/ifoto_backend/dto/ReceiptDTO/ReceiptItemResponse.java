package com.ifoto.ifoto_backend.dto.ReceiptDTO;

public record ReceiptItemResponse(
        String equipmentType,
        String brand,
        String model,
        String serialNumber,
        Long baseAmount,
        Long penaltyAmount,
        Long itemTotal
) {}
