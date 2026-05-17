package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

public record RentalItemResponse(
        Long id,
        Long mainEquipmentId,
        String equipmentType,
        String brand,
        String model,
        String serialNumber,
        String pricingCategory,
        Long baseAmount,
        Long latePenaltyAmount,
        Long itemTotalAmount
) {}
