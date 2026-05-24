package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import com.ifoto.ifoto_backend.validation.DateRangeValidatable;
import com.ifoto.ifoto_backend.validation.DateRangeValid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@DateRangeValid
public record EquipmentRequestRequest(
        @NotNull Long eventId,
        @NotEmpty List<Long> equipmentIds,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        String notes,
        List<EquipmentRequestSubItemRequest> subEquipmentEntries
) implements DateRangeValidatable {

    @Override
    public LocalDate getStartDate() {
        return startDate;
    }

    @Override
    public LocalDate getEndDate() {
        return endDate;
    }
}
