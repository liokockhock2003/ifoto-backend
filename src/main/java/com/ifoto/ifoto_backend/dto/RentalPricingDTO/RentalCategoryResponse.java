package com.ifoto.ifoto_backend.dto.RentalPricingDTO;

import com.ifoto.ifoto_backend.model.enumerator.RentalPricingCategory;

public record RentalCategoryResponse(
        Long id,
        RentalPricingCategory name
) {
}
