package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import java.util.List;

public record RentalReviewRequest(
        String action,
        List<Long> equipmentIds,
        String rejectionReason,
        String committeeNotes
) {}
