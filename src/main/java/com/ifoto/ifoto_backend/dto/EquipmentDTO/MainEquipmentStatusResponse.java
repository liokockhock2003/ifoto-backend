package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;

import java.time.LocalDateTime;

public record MainEquipmentStatusResponse(
        Long id,
        MainEquipmentStatusType statusType,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String notes
) {}
