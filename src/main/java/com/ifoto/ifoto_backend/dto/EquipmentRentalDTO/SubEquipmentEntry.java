package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubEquipmentEntry(
        @NotNull Long subEquipmentId,
        @Min(1) int quantity
) {}
