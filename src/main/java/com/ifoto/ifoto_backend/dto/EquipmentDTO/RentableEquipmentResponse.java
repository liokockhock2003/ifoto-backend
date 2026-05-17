package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.MemberType;
import com.ifoto.ifoto_backend.model.enumerator.RentalPricingCategory;

import java.math.BigDecimal;
import java.util.List;

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
        BigDecimal latePenaltyPerDay,
        List<BookedDateRange> bookedDates
) {}
