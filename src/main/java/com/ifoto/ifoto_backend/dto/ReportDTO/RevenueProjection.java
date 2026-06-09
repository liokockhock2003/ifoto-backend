package com.ifoto.ifoto_backend.dto.ReportDTO;

public interface RevenueProjection {
    String getMonth();
    long getBaseAmount();
    long getPenaltyAmount();
}
