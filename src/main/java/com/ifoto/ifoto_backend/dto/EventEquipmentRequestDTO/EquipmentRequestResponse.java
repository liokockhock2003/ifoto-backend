package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import java.time.LocalDateTime;
import java.util.List;

public record EquipmentRequestResponse(
        Long id,
        String requestNumber,
        Long eventId,
        String eventName,
        String requestedByUsername,
        String reviewedByUsername,
        String reviewedByFullName,
        String status,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        LocalDateTime pickupDatetime,
        LocalDateTime returnDatetime,
        Integer durationDays,
        String rejectionReason,
        String committeeNotes,
        String requesterNotes,
        List<EquipmentRequestItemResponse> items,
        List<EquipmentRequestSubItemResponse> subItems,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        LocalDateTime pickedUpAt
) {}
