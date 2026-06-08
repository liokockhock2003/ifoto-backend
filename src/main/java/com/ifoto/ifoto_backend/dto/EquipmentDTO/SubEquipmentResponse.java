package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.RentalPricingCategory;

import java.util.List;

public record SubEquipmentResponse(
        Long subEquipmentId,
        String type,
        String equipmentType,
        List<String> cameraModel,
        String brand,
        int capacity,
        int totalQuantity,
        int committedQuantity,
        int adminHeldQuantity,
        int availableQuantity,
        String notes,
        Long pricingCategoryId,
        RentalPricingCategory pricingCategoryName,
        boolean isForRent,
        List<SubEquipmentQuantityHoldResponse> quantityHolds,
        List<SubEquipmentBoundaryNote> boundaryNotes
) {}
