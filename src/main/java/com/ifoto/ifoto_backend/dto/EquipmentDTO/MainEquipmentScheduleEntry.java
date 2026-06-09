package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.util.List;

public record MainEquipmentScheduleEntry(
        Long mainEquipmentId,
        String brand,
        String model,
        String serialNumber,
        List<MainEquipmentStatusResponse> statuses
) {}
