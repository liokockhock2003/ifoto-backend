package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDate;

public record SubEquipmentQuantityHoldResponse(
        Long id,
        int quantity,
        LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
