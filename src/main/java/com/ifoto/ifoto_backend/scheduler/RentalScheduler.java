package com.ifoto.ifoto_backend.scheduler;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.EventEquipmentRequest;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.EquipmentRentalRepository;
import com.ifoto.ifoto_backend.repository.EventEquipmentRequestRepository;
import com.ifoto.ifoto_backend.service.EquipmentRentalService;

import jakarta.annotation.PostConstruct;
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
    private final EquipmentRentalService rentalService;

    @PostConstruct
    public void runOnStartup() {
        markActiveRentals();
        markOverdueRentals();
        sendReturnReminders();
        markActiveEventRequests();
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markActiveRentals() {
        List<EquipmentRental> active = rentalRepository
                .findByStatusAndProgramStartDateLessThanEqual(RentalStatus.PAID, LocalDate.now());
        if (active.isEmpty()) return;
        active.forEach(r -> {
            r.setStatus(RentalStatus.ACTIVE);
            r.setActiveAt(LocalDateTime.now());
        });
        rentalRepository.saveAll(active);
        log.info("Marked {} rental(s) as ACTIVE", active.size());
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void markOverdueRentals() {
        rentalService.updateOverduePenalties();
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void sendReturnReminders() {
        rentalService.sendReturnReminders();
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void markActiveEventRequests() {
        List<EventEquipmentRequest> active = eventRequestRepository
                .findByStatusInAndStartDatetimeLessThanEqual(
                        List.of(EventEquipmentRequestStatus.APPROVED, EventEquipmentRequestStatus.PICKED_UP),
                        LocalDateTime.now());
        if (active.isEmpty()) return;
        active.forEach(r -> {
            r.setStatus(EventEquipmentRequestStatus.ACTIVE);
            r.setActiveAt(LocalDateTime.now());
        });
        eventRequestRepository.saveAll(active);
        log.info("Marked {} event equipment request(s) as ACTIVE", active.size());
    }
}
