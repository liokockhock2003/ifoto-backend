package com.ifoto.ifoto_backend.dto.RentalPricingDTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RentalPricingUpdateRequest(
        @NotNull @DecimalMin("0.00") BigDecimal rate1Day,
        @NotNull @DecimalMin("0.00") BigDecimal rate3Days,
        @NotNull @DecimalMin("0.00") BigDecimal ratePerDayExtra,
        @NotNull @DecimalMin("0.00") BigDecimal latePenaltyPerDay
) {
}
