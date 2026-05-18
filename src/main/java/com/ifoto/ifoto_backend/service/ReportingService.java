package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.ReportDTO.ReportingDTO.*;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.EquipmentRentalItemRepository;
import com.ifoto.ifoto_backend.repository.EquipmentRentalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final EquipmentRentalRepository rentalRepository;
    private final EquipmentRentalItemRepository rentalItemRepository;

    private static final List<String> PAID_STATUSES = List.of(
            RentalPaymentStatus.ONLINE_PAID.name(),
            RentalPaymentStatus.CASH_PAID.name(),
            RentalPaymentStatus.PENALTY_PAID.name());

    @Transactional(readOnly = true)
    public KpiResponse kpi() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        long thisMonth = rentalRepository.countCreatedBetween(startOfMonth, startOfNextMonth);
        long revenueCents = rentalRepository.sumRevenue(List.of(
                RentalPaymentStatus.ONLINE_PAID,
                RentalPaymentStatus.CASH_PAID,
                RentalPaymentStatus.PENALTY_PAID));
        long active = rentalRepository.countByStatus(RentalStatus.ACTIVE);
        long overdue = rentalRepository.countByStatus(RentalStatus.OVERDUE);

        return new KpiResponse(thisMonth, toDec(revenueCents), active, overdue);
    }

    @Transactional(readOnly = true)
    public List<StatusBreakdownItem> rentalStatusBreakdown() {
        return rentalRepository.countGroupedByStatus().stream()
                .map(row -> new StatusBreakdownItem(row[0].toString(), ((Number) row[1]).longValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RentalVolumeItem> rentalVolume(int months) {
        int clampedMonths = Math.min(Math.max(months, 1), 60);
        LocalDateTime since = LocalDate.now().withDayOfMonth(1).minusMonths(clampedMonths - 1L).atStartOfDay();

        Map<String, Long> data = rentalRepository.countByMonth(since).stream()
                .collect(Collectors.toMap(r -> r[0].toString(), r -> ((Number) r[1]).longValue()));

        List<RentalVolumeItem> result = new ArrayList<>();
        for (int i = clampedMonths - 1; i >= 0; i--) {
            String month = LocalDate.now().minusMonths(i).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            result.add(new RentalVolumeItem(month, data.getOrDefault(month, 0L)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RevenueItem> revenue(int months) {
        int clampedMonths = Math.min(Math.max(months, 1), 60);
        LocalDateTime since = LocalDate.now().withDayOfMonth(1).minusMonths(clampedMonths - 1L).atStartOfDay();

        Map<String, long[]> data = rentalRepository.revenueByMonth(since, PAID_STATUSES).stream()
                .collect(Collectors.toMap(
                        r -> r[0].toString(),
                        r -> new long[]{((Number) r[1]).longValue(), ((Number) r[2]).longValue()}));

        List<RevenueItem> result = new ArrayList<>();
        for (int i = clampedMonths - 1; i >= 0; i--) {
            String month = LocalDate.now().minusMonths(i).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            long[] amounts = data.getOrDefault(month, new long[]{0L, 0L});
            result.add(new RevenueItem(month, toDec(amounts[0]), toDec(amounts[1])));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<EquipmentUtilizationItem> equipmentUtilization() {
        return rentalItemRepository.equipmentUtilization().stream()
                .map(row -> {
                    Long id = ((Number) row[0]).longValue();
                    String brand = row[1] != null ? row[1].toString() : "";
                    String model = row[2] != null ? row[2].toString() : "";
                    String name = (brand + " " + model).trim();
                    String category = row[3] != null ? row[3].toString() : "";
                    long count = ((Number) row[4]).longValue();
                    return new EquipmentUtilizationItem(id, name, count, category);
                })
                .toList();
    }

    private BigDecimal toDec(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
