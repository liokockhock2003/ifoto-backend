package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import java.time.LocalDateTime;

public record SubEquipmentBoundaryNote(
        Long rentalId,
        Long holdId,
        LocalDateTime availableAfter,
        LocalDateTime mustReturnBefore,
        int quantity
) {}
