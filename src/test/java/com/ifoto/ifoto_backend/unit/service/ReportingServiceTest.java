package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.dto.ReportDTO.RentalVolumeProjection;
import com.ifoto.ifoto_backend.dto.ReportDTO.RevenueProjection;
import com.ifoto.ifoto_backend.dto.ReportDTO.ReportingDTO.RentalVolumeItem;
import com.ifoto.ifoto_backend.dto.ReportDTO.ReportingDTO.RevenueItem;
import com.ifoto.ifoto_backend.repository.EquipmentRentalItemRepository;
import com.ifoto.ifoto_backend.repository.EquipmentRentalRepository;
import com.ifoto.ifoto_backend.service.ReportingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private EquipmentRentalRepository rentalRepository;
    @Mock private EquipmentRentalItemRepository rentalItemRepository;

    @InjectMocks private ReportingService service;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ── rentalVolume ─────────────────────────────────────────────────────────

    @Test
    void rentalVolume_monthsZero_clampedToOne() {
        when(rentalRepository.countByMonth(any())).thenReturn(List.of());

        List<RentalVolumeItem> result = service.rentalVolume(0);

        assertEquals(1, result.size());
    }

    @Test
    void rentalVolume_monthsNegative_clampedToOne() {
        when(rentalRepository.countByMonth(any())).thenReturn(List.of());

        List<RentalVolumeItem> result = service.rentalVolume(-5);

        assertEquals(1, result.size());
    }

    @Test
    void rentalVolume_monthsOver60_clampedTo60() {
        when(rentalRepository.countByMonth(any())).thenReturn(List.of());

        List<RentalVolumeItem> result = service.rentalVolume(100);

        assertEquals(60, result.size());
    }

    @Test
    void rentalVolume_months12_returns12Items() {
        when(rentalRepository.countByMonth(any())).thenReturn(List.of());

        List<RentalVolumeItem> result = service.rentalVolume(12);

        assertEquals(12, result.size());
    }

    @Test
    void rentalVolume_missingMonths_backfilledWithZero() {
        when(rentalRepository.countByMonth(any())).thenReturn(List.of());

        List<RentalVolumeItem> result = service.rentalVolume(3);

        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(item -> item.count() == 0L));
    }

    @Test
    void rentalVolume_monthWithData_correctlyMapped() {
        String lastMonth = LocalDate.now().minusMonths(1).format(MONTH_FMT);
        RentalVolumeProjection proj = mock(RentalVolumeProjection.class);
        when(proj.getMonth()).thenReturn(lastMonth);
        when(proj.getCount()).thenReturn(7L);
        when(rentalRepository.countByMonth(any())).thenReturn(List.of(proj));

        List<RentalVolumeItem> result = service.rentalVolume(3);

        RentalVolumeItem matched = result.stream()
                .filter(i -> i.month().equals(lastMonth))
                .findFirst().orElseThrow();
        assertEquals(7L, matched.count());
        long zeroCount = result.stream().filter(i -> i.count() == 0L).count();
        assertEquals(2L, zeroCount);
    }

    @Test
    void rentalVolume_monthLabelsMatchExpectedFormat() {
        when(rentalRepository.countByMonth(any())).thenReturn(List.of());

        List<RentalVolumeItem> result = service.rentalVolume(6);

        result.forEach(item -> assertTrue(item.month().matches("\\d{4}-\\d{2}"),
                "Expected yyyy-MM but got: " + item.month()));
    }

    // ── revenue ──────────────────────────────────────────────────────────────

    @Test
    void revenue_monthsZero_clampedToOne() {
        when(rentalRepository.revenueByMonth(any(), anyList())).thenReturn(List.of());

        List<RevenueItem> result = service.revenue(0);

        assertEquals(1, result.size());
    }

    @Test
    void revenue_monthsOver60_clampedTo60() {
        when(rentalRepository.revenueByMonth(any(), anyList())).thenReturn(List.of());

        List<RevenueItem> result = service.revenue(100);

        assertEquals(60, result.size());
    }

    @Test
    void revenue_amountsConvertedFromCentsToBigDecimal() {
        String lastMonth = LocalDate.now().minusMonths(1).format(MONTH_FMT);
        RevenueProjection proj = mock(RevenueProjection.class);
        when(proj.getMonth()).thenReturn(lastMonth);
        when(proj.getBaseAmount()).thenReturn(100000L);
        when(proj.getPenaltyAmount()).thenReturn(5000L);
        when(rentalRepository.revenueByMonth(any(), anyList())).thenReturn(List.of(proj));

        List<RevenueItem> result = service.revenue(2);

        RevenueItem matched = result.stream()
                .filter(i -> i.month().equals(lastMonth))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("1000.00"), matched.baseAmount());
        assertEquals(new BigDecimal("50.00"), matched.penaltyAmount());
    }

    @Test
    void revenue_missingMonths_backfilledWithZero() {
        when(rentalRepository.revenueByMonth(any(), anyList())).thenReturn(List.of());

        List<RevenueItem> result = service.revenue(2);

        assertEquals(2, result.size());
        result.forEach(item -> {
            assertEquals(new BigDecimal("0.00"), item.baseAmount());
            assertEquals(new BigDecimal("0.00"), item.penaltyAmount());
        });
    }
}
