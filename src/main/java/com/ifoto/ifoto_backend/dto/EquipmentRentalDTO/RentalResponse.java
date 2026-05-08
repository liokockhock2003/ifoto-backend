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
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate approvedStartDate,
        LocalDate approvedEndDate,
        Integer durationDays,
        Long totalBaseAmount,
        Long totalPenaltyAmount,
        Long totalAmount,
        String rejectionReason,
        String committeeNotes,
        String renterNotes,
        List<RentalItemResponse> items,
        LocalDateTime createdAt
) {}
