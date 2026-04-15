package com.ifoto.ifoto_backend.dto.EquipmentDTO;

public record MainEquipmentResponse(
        Long mainEquipmentId,
        String equipmentType,
        String lensType,
        String brand,
        String model,
        String serialNumber,
        String condition,
        String status,
        String notes
) {}
