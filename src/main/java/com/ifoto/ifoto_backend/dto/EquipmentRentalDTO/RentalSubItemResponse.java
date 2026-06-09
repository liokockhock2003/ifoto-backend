package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import java.util.List;

public record RentalSubItemResponse(
        Long id,
        Long subEquipmentId,
        String type,
        String equipmentType,
        List<String> cameraModel,
        String brand,
        int borrowedQuantity,
        Long baseAmount,
        Long latePenaltyAmount,
        Long itemTotalAmount
) {}
