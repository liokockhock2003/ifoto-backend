package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;

import java.time.LocalDate;

public record MainEquipmentStatusResponse(
        Long id,
        MainEquipmentStatusType statusType,
        LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
