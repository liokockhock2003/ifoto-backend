package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.validation.DateRangeValid;
import com.ifoto.ifoto_backend.validation.DateRangeValidatable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@DateRangeValid
public record SubEquipmentQuantityHoldRequest(
        @Min(1) int quantity,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String notes
) implements DateRangeValidatable {

    @Override
    public LocalDate getStartDate() { return startDate; }

    @Override
    public LocalDate getEndDate() { return endDate; }
}
