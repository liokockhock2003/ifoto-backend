package com.ifoto.ifoto_backend.dto.EquipmentDTO;

public record SubEquipmentResponse(
        Long subEquipmentId,
        String equipmentType,
        String brand,
        String model,
        int capacity,
        int totalQuantity,
        int usedQuantity,
        int availableQuantity,
        String notes
) {}
