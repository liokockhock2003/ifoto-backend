package com.ifoto.ifoto_backend.dto.ReportDTO;

import java.math.BigDecimal;

public class ReportingDTO {

    public record KpiResponse(
            long totalRentalsThisMonth,
            BigDecimal totalRevenueCollected,
            long activeRentals,
            long overdueCount) {}

    public record StatusBreakdownItem(String status, long count) {}

    public record RentalStatusSummary(
            long approved,
            long paid,
            long active,
            long overdue,
            long paidOverdue,
            long returned
    ) {}

    public record RentalVolumeItem(String month, long count) {}

    public record RevenueItem(String month, BigDecimal baseAmount, BigDecimal penaltyAmount) {}

    public record EquipmentUtilizationItem(
            Long equipmentId,
            String equipmentName,
            long rentalCount,
            String category) {}
}
