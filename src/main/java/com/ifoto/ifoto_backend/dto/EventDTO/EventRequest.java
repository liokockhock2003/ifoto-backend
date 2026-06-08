package com.ifoto.ifoto_backend.dto.EventDTO;

import com.ifoto.ifoto_backend.validation.DateTimeRangeValid;
import com.ifoto.ifoto_backend.validation.DateTimeRangeValidatable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

@DateTimeRangeValid
public record EventRequest(
        @NotBlank(message = "Event name is required")
        String eventName,

        String description,

        @NotNull(message = "Start datetime is required")
        LocalDateTime startDatetime,

        @NotNull(message = "End datetime is required")
        LocalDateTime endDatetime,

        String location,

        Boolean isActive,

        List<Long> committeeUserIds) implements DateTimeRangeValidatable {

    @Override
    public LocalDateTime getStartDatetime() { return startDatetime; }

    @Override
    public LocalDateTime getEndDatetime() { return endDatetime; }
}
