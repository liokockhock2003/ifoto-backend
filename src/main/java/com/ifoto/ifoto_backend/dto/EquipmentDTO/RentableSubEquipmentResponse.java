package com.ifoto.ifoto_backend.dto.EquipmentDTO;

import com.ifoto.ifoto_backend.model.enumerator.MemberType;
import com.ifoto.ifoto_backend.model.enumerator.RentalPricingCategory;

import java.math.BigDecimal;
import java.util.List;

public record RentableSubEquipmentResponse(
        Long subEquipmentId,
        String type,
        String equipmentType,
        List<String> cameraModel,
        String brand,
        int totalQuantity,
        String notes,
        Long pricingCategoryId,
        RentalPricingCategory pricingCategory,
        MemberType memberType,
        BigDecimal rate1Day,
        BigDecimal rate3Days,
        BigDecimal ratePerDayExtra,
        BigDecimal latePenaltyPerDay,
        List<SubEquipmentBookedRange> bookedDates,
        List<SubEquipmentQuantityHoldResponse> adminHolds
) {}
