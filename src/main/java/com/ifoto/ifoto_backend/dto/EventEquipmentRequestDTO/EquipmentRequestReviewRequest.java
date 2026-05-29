package com.ifoto.ifoto_backend.dto.EventEquipmentRequestDTO;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record EquipmentRequestReviewRequest(
        @NotBlank String action,
        List<Long> equipmentIds,
        List<EquipmentRequestSubItemRequest> subEquipmentEntries,
        String rejectionReason,
        String committeeNotes
) {}
