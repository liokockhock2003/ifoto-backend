package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.util.List;

public record SubEquipmentScheduleEntry(
        Long subEquipmentId,
        String type,
        String brand,
        List<String> cameraModel,
        int borrowedQuantity,
        List<SubEquipmentQuantityHoldResponse> holds
) {}
