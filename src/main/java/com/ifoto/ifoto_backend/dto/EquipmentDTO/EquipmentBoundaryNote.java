package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDateTime;

public record EquipmentBoundaryNote(
        Long rentalId,
        Long statusId,
        Long eventRequestId,
        LocalDateTime availableAfter,
        LocalDateTime mustReturnBefore
) {}
