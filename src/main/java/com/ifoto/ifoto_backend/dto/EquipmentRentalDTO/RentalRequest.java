package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import com.ifoto.ifoto_backend.validation.DateRangeValid;
import com.ifoto.ifoto_backend.validation.DateRangeValidatable;

import java.time.LocalDate;
import java.util.List;

@DateRangeValid
public record RentalRequest(
        List<Long> equipmentIds,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        List<SubEquipmentEntry> subEquipmentEntries
) implements DateRangeValidatable {

    @Override
    public LocalDate getStartDate() { return startDate; }

    @Override
    public LocalDate getEndDate() { return endDate; }
}
