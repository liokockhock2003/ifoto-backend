package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.util.List;

public record SubEquipmentResponse(
        Long subEquipmentId,
        String type,
        String equipmentType,
        List<String> cameraModel,
        String brand,
        int capacity,
        int totalQuantity,
        int usedQuantity,
        int availableQuantity,
        String notes
) {}
