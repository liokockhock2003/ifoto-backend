package com.ifoto.ifoto_backend.dto.EventDTO;

import com.ifoto.ifoto_backend.validation.EventDateRangeValid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

@EventDateRangeValid
public record EventRequest(
        @NotBlank(message = "Event name is required")
        String eventName,

        String description,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        String location,

        Boolean isActive,

        List<Long> committeeUserIds) {
}
