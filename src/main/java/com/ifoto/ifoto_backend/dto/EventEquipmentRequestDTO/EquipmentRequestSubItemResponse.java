package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import java.util.List;

public record EquipmentRequestSubItemResponse(
        Long id,
        Long subEquipmentId,
        String type,
        String equipmentType,
        List<String> cameraModel,
        String brand,
        int borrowedQuantity
) {}
