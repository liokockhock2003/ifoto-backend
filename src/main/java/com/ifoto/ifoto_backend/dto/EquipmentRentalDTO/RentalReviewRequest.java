package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import java.time.LocalDateTime;
import java.util.List;

public record RentalReviewRequest(
        String action,
        List<Long> equipmentIds,
        List<SubEquipmentEntry> subEquipmentEntries,
        String rejectionReason,
        String committeeNotes,
        LocalDateTime pickupDatetime,
        LocalDateTime returnDatetime
) {}
