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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final EquipmentRentalRepository rentalRepository;
    private final EquipmentRentalItemRepository rentalItemRepository;

    private static final List<RentalPaymentStatus> PAID_STATUSES =
            Arrays.stream(RentalPaymentStatus.values())
                    .filter(RentalPaymentStatus::isPaid)
                    .toList();

    private static final List<String> PAID_STATUS_NAMES =
            PAID_STATUSES.stream().map(Enum::name).toList();

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public KpiResponse kpi() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        long thisMonth = rentalRepository.countCreatedBetween(startOfMonth, startOfNextMonth);
        long revenueCents = rentalRepository.sumRevenue(PAID_STATUSES);
        long active = rentalRepository.countByStatus(RentalStatus.ACTIVE);
        long overdue = rentalRepository.countByStatus(RentalStatus.OVERDUE);

        return new KpiResponse(thisMonth, toDec(revenueCents), active, overdue);
    }

    @Transactional(readOnly = true)
    public RentalStatusSummary rentalStatusBreakdown() {
        return new RentalStatusSummary(
                rentalRepository.countByStatus(RentalStatus.APPROVED),
                rentalRepository.countByStatus(RentalStatus.PAID),
                rentalRepository.countByStatus(RentalStatus.ACTIVE),
                rentalRepository.countByStatus(RentalStatus.OVERDUE),
                rentalRepository.countByPaymentStatus(RentalPaymentStatus.PENALTY_PAID),
                rentalRepository.countByStatus(RentalStatus.RETURNED)
        );
    }

    @Transactional(readOnly = true)
    public List<RentalVolumeItem> rentalVolume(int months) {
        int clamped = Math.min(Math.max(months, 1), 60);
        LocalDate now = LocalDate.now();
        LocalDateTime since = now.withDayOfMonth(1).minusMonths(clamped - 1L).atStartOfDay();

        Map<String, Long> data = rentalRepository.countByMonth(since).stream()
                .collect(Collectors.toMap(p -> p.getMonth(), p -> p.getCount()));

        List<RentalVolumeItem> result = new ArrayList<>();
        for (int i = clamped - 1; i >= 0; i--) {
            String month = now.minusMonths(i).format(MONTH_FMT);
            result.add(new RentalVolumeItem(month, data.getOrDefault(month, 0L)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<RevenueItem> revenue(int months) {
        int clamped = Math.min(Math.max(months, 1), 60);
        LocalDate now = LocalDate.now();
        LocalDateTime since = now.withDayOfMonth(1).minusMonths(clamped - 1L).atStartOfDay();

        Map<String, long[]> data = rentalRepository.revenueByMonth(since, PAID_STATUS_NAMES).stream()
                .collect(Collectors.toMap(
                        p -> p.getMonth(),
                        p -> new long[]{p.getBaseAmount(), p.getPenaltyAmount()}));

        List<RevenueItem> result = new ArrayList<>();
        for (int i = clamped - 1; i >= 0; i--) {
            String month = now.minusMonths(i).format(MONTH_FMT);
            long[] amounts = data.getOrDefault(month, new long[]{0L, 0L});
            result.add(new RevenueItem(month, toDec(amounts[0]), toDec(amounts[1])));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<EquipmentUtilizationItem> equipmentUtilization() {
        return rentalItemRepository.equipmentUtilization().stream()
                .map(p -> {
                    String brand = p.getBrand() != null ? p.getBrand() : "";
                    String model = p.getModel() != null ? p.getModel() : "";
                    String name = (brand + " " + model).trim();
                    String category = p.getEquipmentType() != null ? p.getEquipmentType() : "";
                    return new EquipmentUtilizationItem(p.getEquipmentId(), name, p.getRentalCount(), category);
                })
                .toList();
    }

    private BigDecimal toDec(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
