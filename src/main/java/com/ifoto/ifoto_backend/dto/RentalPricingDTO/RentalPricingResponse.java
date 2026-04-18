package com.ifoto.ifoto_backend.dto.RentalPricingDTO;

import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.model.RentalPricingCategory;

import java.math.BigDecimal;

public record RentalPricingResponse(
        Long id,
        Long pricingCategoryId,
        RentalPricingCategory category,
        MemberType memberType,
        BigDecimal rate1Day,
        BigDecimal rate3Days,
        BigDecimal ratePerDayExtra,
        BigDecimal latePenaltyPerDay
) {
}
