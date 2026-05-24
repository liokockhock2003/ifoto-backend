package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

public record EquipmentRequestItemResponse(
        Long id,
        Long mainEquipmentId,
        String equipmentType,
        String brand,
        String model,
        String serialNumber
) {}
