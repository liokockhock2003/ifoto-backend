package com.ifoto.ifoto_backend.dto.RentalPricingDTO;

import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.model.RentalPricingCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RentalPricingBulkUpdateRequest(
        @NotNull @Size(min = 1) List<@Valid BulkItem> items
) {
    public record BulkItem(
            @NotNull RentalPricingCategory category,
            @NotNull MemberType memberType,
            @NotNull @DecimalMin("0.00") BigDecimal rate1Day,
            @NotNull @DecimalMin("0.00") BigDecimal rate3Days,
            @NotNull @DecimalMin("0.00") BigDecimal ratePerDayExtra,
            @NotNull @DecimalMin("0.00") BigDecimal latePenaltyPerDay
    ) {}
}
