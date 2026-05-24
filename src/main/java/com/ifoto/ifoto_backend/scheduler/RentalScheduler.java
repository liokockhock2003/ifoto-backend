package com.ifoto.ifoto_backend.scheduler;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.EventEquipmentRequest;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.EquipmentRentalRepository;
import com.ifoto.ifoto_backend.repository.EventEquipmentRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RentalScheduler {

    private final EquipmentRentalRepository rentalRepository;
    private final EventEquipmentRequestRepository eventRequestRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markActiveRentals() {
        List<EquipmentRental> active = rentalRepository
                .findByStatusAndApprovedStartDateLessThanEqual(RentalStatus.PAID, LocalDate.now());
        if (active.isEmpty()) return;
        active.forEach(r -> {
            r.setStatus(RentalStatus.ACTIVE);
            r.setActiveAt(LocalDateTime.now());
        });
        rentalRepository.saveAll(active);
        log.info("Marked {} rental(s) as ACTIVE", active.size());
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markOverdueRentals() {
        LocalDate today = LocalDate.now();

        List<EquipmentRental> newlyOverdue = rentalRepository
                .findByStatusAndDueReturnDateBefore(RentalStatus.ACTIVE, today);
        newlyOverdue.forEach(r -> r.setStatus(RentalStatus.OVERDUE));

        List<EquipmentRental> alreadyOverdue = rentalRepository.findByStatus(RentalStatus.OVERDUE);

        for (EquipmentRental r : alreadyOverdue) {
            long days = today.toEpochDay() - r.getDueReturnDate().toEpochDay();
            long total = 0L;
            for (var item : r.getItems()) {
                long penalty = item.getLatePenaltyPerDay() * days;
                item.setLatePenaltyAmount(penalty);
                item.setItemTotalAmount(item.getBaseAmount() + penalty);
                total += penalty;
            }
            r.setTotalPenaltyAmount(total);
            r.setTotalAmount(r.getTotalBaseAmount() + total);
        }

        rentalRepository.saveAll(newlyOverdue);
        rentalRepository.saveAll(alreadyOverdue);
        log.info("Marked {} new OVERDUE, recalculated penalty for {} existing OVERDUE rental(s)",
                newlyOverdue.size(), alreadyOverdue.size());
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markActiveEventRequests() {
        List<EventEquipmentRequest> active = eventRequestRepository
                .findByStatusAndApprovedStartDateLessThanEqual(
                        EventEquipmentRequestStatus.APPROVED, LocalDate.now());
        if (active.isEmpty()) return;
        active.forEach(r -> {
            r.setStatus(EventEquipmentRequestStatus.ACTIVE);
            r.setActiveAt(LocalDateTime.now());
        });
        eventRequestRepository.saveAll(active);
        log.info("Marked {} event equipment request(s) as ACTIVE", active.size());
    }
}
