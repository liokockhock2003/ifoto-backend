package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDate;

public record SubEquipmentBookedRange(
        Long subEquipmentId,
        LocalDate startDate,
        LocalDate endDate,
        int committedQuantity,
        boolean pending
) {}
