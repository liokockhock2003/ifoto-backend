package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.MainEquipmentStatusType;
import com.ifoto.ifoto_backend.validation.DateTimeRangeValid;
import com.ifoto.ifoto_backend.validation.DateTimeRangeValidatable;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@DateTimeRangeValid
public record MainEquipmentStatusRequest(
        @NotNull MainEquipmentStatusType statusType,
        @NotNull LocalDateTime startDatetime,
        @NotNull LocalDateTime endDatetime,
        String notes
) implements DateTimeRangeValidatable {

    @Override
    public LocalDateTime getStartDatetime() { return startDatetime; }

    @Override
    public LocalDateTime getEndDatetime() { return endDatetime; }
}
