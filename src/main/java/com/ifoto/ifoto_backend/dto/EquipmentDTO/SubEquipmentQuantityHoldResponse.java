package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDateTime;

public record SubEquipmentQuantityHoldResponse(
        Long id,
        int quantity,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String notes
) {}
