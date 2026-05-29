package com.ifoto.ifoto_backend.dto.ReceiptDTO;

public record ReceiptSubItemResponse(
        String type,
        String equipmentType,
        String brand,
        int borrowedQuantity,
        Long baseAmount,
        Long penaltyAmount,
        Long itemTotal
) {}
