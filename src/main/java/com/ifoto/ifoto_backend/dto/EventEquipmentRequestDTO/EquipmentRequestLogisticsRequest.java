package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record EquipmentRequestLogisticsRequest(
        @NotNull LocalDateTime pickupDatetime,
        @NotNull LocalDateTime returnDatetime
) {}
