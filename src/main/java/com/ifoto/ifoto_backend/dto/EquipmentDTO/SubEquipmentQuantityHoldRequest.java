package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.validation.DateTimeRangeValid;
import com.ifoto.ifoto_backend.validation.DateTimeRangeValidatable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@DateTimeRangeValid
public record SubEquipmentQuantityHoldRequest(
        @Min(1) int quantity,
        @NotNull LocalDateTime startDatetime,
        @NotNull LocalDateTime endDatetime,
        String notes
) implements DateTimeRangeValidatable {

    @Override
    public LocalDateTime getStartDatetime() { return startDatetime; }

    @Override
    public LocalDateTime getEndDatetime() { return endDatetime; }
}
