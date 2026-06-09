package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record RentalResponse(
        Long id,
        String rentalNumber,
        String renterUsername,
        String status,
        String paymentMethod,
        String paymentStatus,
        LocalDate programStartDate,
        LocalDate programEndDate,
        LocalDateTime pickupDatetime,
        LocalDateTime returnDatetime,
        Integer durationDays,
        Long totalBaseAmount,
        Long totalPenaltyAmount,
        Long totalAmount,
        String rejectionReason,
        String committeeNotes,
        String renterNotes,
        LocalDateTime pickedUpAt,
        List<RentalItemResponse> items,
        List<RentalSubItemResponse> subItems,
        LocalDateTime createdAt,
        String reviewedByUsername,
        String reviewedByFullName,
        LocalDateTime approvedAt
) {}
