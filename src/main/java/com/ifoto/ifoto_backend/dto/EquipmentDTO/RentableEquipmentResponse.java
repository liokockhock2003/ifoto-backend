package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.model.RentalPricingCategory;

import java.math.BigDecimal;

public record RentableEquipmentResponse(
        Long mainEquipmentId,
        String equipmentType,
        String lensType,
        String brand,
        String model,
        String serialNumber,
        String condition,
        String status,
        String notes,
        Long pricingCategoryId,
        RentalPricingCategory pricingCategory,
        MemberType memberType,
        BigDecimal rate1Day,
        BigDecimal rate3Days,
        BigDecimal ratePerDayExtra,
        BigDecimal latePenaltyPerDay
) {}
