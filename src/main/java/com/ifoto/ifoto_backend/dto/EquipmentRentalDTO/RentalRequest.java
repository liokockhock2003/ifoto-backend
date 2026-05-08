package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import java.time.LocalDate;
import java.util.List;

public record RentalRequest(
        List<Long> equipmentIds,
        LocalDate startDate,
        LocalDate endDate,
        String notes
) {}
