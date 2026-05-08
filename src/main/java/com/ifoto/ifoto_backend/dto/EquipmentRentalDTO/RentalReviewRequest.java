package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import java.time.LocalDate;
import java.util.List;

public record RentalReviewRequest(
        String action,
        LocalDate approvedStartDate,
        LocalDate approvedEndDate,
        List<Long> equipmentIds,
        String rejectionReason,
        String committeeNotes
) {}
