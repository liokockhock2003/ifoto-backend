package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record RentalLogisticsRequest(
        @NotNull(message = "pickupDatetime is required") LocalDateTime pickupDatetime,
        @NotNull(message = "returnDatetime is required") LocalDateTime returnDatetime
) {}
