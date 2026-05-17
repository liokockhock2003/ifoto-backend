package com.ifoto.ifoto_backend.dto.EquipmentRentalDTO;

import com.ifoto.ifoto_backend.validation.DateRangeValid;
import com.ifoto.ifoto_backend.validation.DateRangeValidatable;

import java.time.LocalDate;
import java.util.List;

@DateRangeValid
public record RentalReviewRequest(
        String action,
        LocalDate approvedStartDate,
        LocalDate approvedEndDate,
        List<Long> equipmentIds,
        String rejectionReason,
        String committeeNotes
) implements DateRangeValidatable {

    @Override
    public LocalDate getStartDate() { return approvedStartDate; }

    @Override
    public LocalDate getEndDate() { return approvedEndDate; }
}
