package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EquipmentRequestSubItemRequest(
        @NotNull Long subEquipmentId,
        @Min(1) int quantity
) {}
