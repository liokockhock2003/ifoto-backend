package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record EquipmentRequestEquipmentUpdateRequest(
        @NotNull @NotEmpty List<Long> equipmentIds,
        List<EquipmentRequestSubItemRequest> subEquipmentEntries
) {}
