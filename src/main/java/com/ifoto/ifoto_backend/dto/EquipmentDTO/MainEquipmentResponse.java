package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.RentalPricingCategory;

import java.util.List;

public record MainEquipmentResponse(
        Long mainEquipmentId,
        String equipmentType,
        String lensType,
        String brand,
        String model,
        String serialNumber,
        String condition,
        String status,
        String notes,
        Long pricingCategoryId,
        RentalPricingCategory pricingCategory,
        boolean isForRent,
        List<MainEquipmentStatusResponse> dateStatuses
) {}
