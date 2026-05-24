package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record EquipmentRequestResponse(
        Long id,
        String requestNumber,
        Long eventId,
        String eventName,
        String requestedByUsername,
        String reviewedByUsername,
        String status,
        LocalDate requestedStartDate,
        LocalDate requestedEndDate,
        LocalDate approvedStartDate,
        LocalDate approvedEndDate,
        Integer durationDays,
        String rejectionReason,
        String committeeNotes,
        String requesterNotes,
        List<EquipmentRequestItemResponse> items,
        List<EquipmentRequestSubItemResponse> subItems,
        LocalDateTime createdAt
) {}
