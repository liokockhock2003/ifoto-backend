package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RentalEquipmentUpdateRequest(
        @NotEmpty(message = "At least one equipment ID is required") List<Long> equipmentIds,
        List<SubEquipmentEntry> subEquipmentEntries  // nullable — omit to keep existing sub-items
) {}
