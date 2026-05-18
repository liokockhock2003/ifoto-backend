package com.ifoto.ifoto_backend.dto.ReportDTO;

import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;

public interface StatusBreakdownProjection {
    RentalStatus getStatus();
    long getCount();
}
