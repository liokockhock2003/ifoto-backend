package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.dto.EquipmentRentalDTO.SubEquipmentEntry;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.*;
import com.ifoto.ifoto_backend.repository.*;
import com.ifoto.ifoto_backend.service.payment.PaymentMethodHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EquipmentRentalService {

        private final EquipmentRentalRepository rentalRepository;
        private final EquipmentRentalItemRepository rentalItemRepository;
        private final EquipmentRentalSubItemRepository rentalSubItemRepository;
        private final SubEquipmentRepository subEquipmentRepository;
        private final MainEquipmentRepository mainEquipmentRepository;
        private final MainEquipmentStatusRepository mainEquipmentStatusRepository;
        private final SubEquipmentQuantityHoldRepository subEquipmentQuantityHoldRepository;
        private final RentalPricingRepository pricingRepository;
        private final RentalPricingService rentalPricingService;
        private final UserRepository userRepository;
        private final PaymentService paymentService;
        private final ReceiptService receiptService;
        private final MailService mailService;
        private final List<PaymentMethodHandler> paymentHandlers;
        private final EventEquipmentRequestItemRepository requestEventItemRepository;

        @Value("${app.rental.buffer-minutes:60}")
        private int bufferMinutes;

        private static final List<RentalStatus> BLOCKING_STATUSES = List.of(
                        RentalStatus.APPROVED, RentalStatus.PICKED_UP, RentalStatus.PENDING_PAYMENT,
                        RentalStatus.PAID, RentalStatus.ACTIVE, RentalStatus.OVERDUE);

        private static final List<EventEquipmentRequestStatus> EVENT_BLOCKING_STATUSES = List
                        .of(EventEquipmentRequestStatus.APPROVED, EventEquipmentRequestStatus.ACTIVE);

        private Map<RentalPaymentMethod, PaymentMethodHandler> handlerMap;

        @PostConstruct
        void initHandlerMap() {
                handlerMap = paymentHandlers.stream()
                                .collect(Collectors.toMap(PaymentMethodHandler::getMethod, h -> h));
        }

        // ── Submit ────────────────────────────────────────────────────────────────

        @Transactional
        public EquipmentRental submitRental(List<Long> equipmentIds, LocalDate startDate, LocalDate endDate,
                        String renterNotes, String username, List<SubEquipmentEntry> subEquipmentEntries) {
                User renter = findUser(username);
                MemberType memberType = resolveMemberType(renter);

                List<MainEquipment> equipmentList = resolveAndValidateEquipment(equipmentIds);

                // Only block submission when a status record has interior overlap with the
                // program dates (boundary-only contact = PARTIALLY_AVAILABLE = allowed).
                // Exact datetime conflicts are enforced later at review when pickup/return
                // times are set.
                checkStatusConflictsInterior(equipmentList, startDate, endDate);

                EquipmentRental rental = EquipmentRental.builder()
                                .renter(renter)
                                .status(RentalStatus.PENDING_REVIEW)
                                .programStartDate(startDate)
                                .programEndDate(endDate)
                                .renterNotes(renterNotes)
                                .build();
                rental = rentalRepository.save(rental);

                int year = LocalDateTime.now().getYear();
                rental.setRentalNumber("RNT-%d-%06d".formatted(year, rental.getId()));

                int requestedDays = (int) (endDate.toEpochDay() - startDate.toEpochDay() + 1);
                List<EquipmentRentalItem> items = buildItems(rental, equipmentList, memberType, requestedDays);
                rental.getItems().addAll(items);
                long totalBase = items.stream().mapToLong(EquipmentRentalItem::getBaseAmount).sum();
                rental.setTotalBaseAmount(totalBase);
                rental.setTotalPenaltyAmount(0L);
                rental.setTotalAmount(totalBase);
                rental = rentalRepository.save(rental);

                if (subEquipmentEntries != null && !subEquipmentEntries.isEmpty()) {
                        long subTotal = buildAndSaveSubItems(rental, subEquipmentEntries, memberType, requestedDays,
                                        startDate,
                                        endDate, false);
                        rental.setTotalBaseAmount(rental.getTotalBaseAmount() + subTotal);
                        rental.setTotalAmount(rental.getTotalBaseAmount());
                        rental = rentalRepository.save(rental);
                }

                List<String> committeeEmails = userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")
                                .stream().map(User::getEmail).toList();
                if (!committeeEmails.isEmpty()) {
                        List<String[]> mainEqRows = equipmentList.stream()
                                        .map(e -> new String[] {
                                                        e.getBrand() + " " + e.getModel(),
                                                        e.getSerialNumber() != null ? e.getSerialNumber() : "—" })
                                        .toList();
                        List<String[]> subEqRows = rental.getSubItems().stream()
                                        .map(s -> new String[] {
                                                        s.getSubEquipment().getType() + " "
                                                                        + s.getSubEquipment().getBrand(),
                                                        "x" + s.getBorrowedQuantity() })
                                        .toList();
                        mailService.sendRentalSubmittedToCommittee(committeeEmails, rental.getRentalNumber(),
                                        renter.getFullName() != null ? renter.getFullName() : renter.getUsername(),
                                        mainEqRows, subEqRows, startDate, endDate);
                }
                return rental;
        }

        // ── Review (approve / reject) ─────────────────────────────────────────────

        @Transactional
        public EquipmentRental reviewRental(Long id, String action,
                        List<Long> equipmentIds, List<SubEquipmentEntry> subEquipmentEntries,
                        String rejectionReason, String committeeNotes, String username,
                        LocalDateTime pickupDatetime, LocalDateTime returnDatetime) {
                User committee = findUser(username);
                EquipmentRental rental = findRental(id);

                if (rental.getStatus() != RentalStatus.PENDING_REVIEW) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rental is not in PENDING REVIEW status");
                }

                if ("REJECT".equalsIgnoreCase(action)) {
                        rental.setStatus(RentalStatus.REJECTED);
                        rental.setReviewedBy(committee);
                        rental.setRejectionReason(rejectionReason);
                        rental.setCommitteeNotes(committeeNotes);
                        mailService.sendRentalRejectedToRenter(rental.getRenter().getEmail(),
                                        rental.getRentalNumber(), rejectionReason);
                        return rentalRepository.save(rental);
                }

                if (!"APPROVE".equalsIgnoreCase(action)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action must be APPROVE or REJECT");
                }

                LocalDate programStart = rental.getProgramStartDate();
                LocalDate programEnd = rental.getProgramEndDate();

                LocalDateTime pickupDt = (pickupDatetime != null) ? pickupDatetime : programStart.atStartOfDay();
                LocalDateTime returnDt = (returnDatetime != null) ? returnDatetime : programEnd.atTime(23, 59, 59);

                validateLogisticsBounds(pickupDt, returnDt, programStart, programEnd);

                List<Long> finalEquipmentIds = (equipmentIds != null && !equipmentIds.isEmpty())
                                ? equipmentIds
                                : rental.getItems().stream().map(i -> i.getMainEquipment().getMainEquipmentId())
                                                .toList();
                List<MainEquipment> equipmentList = resolveAndValidateEquipment(finalEquipmentIds);

                LocalDateTime effectiveStart = pickupDt.minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = returnDt.plusMinutes(bufferMinutes);
                checkRentalConflicts(equipmentList, effectiveStart, effectiveEnd, id);
                checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

                int durationDays = (int) (programEnd.toEpochDay() - programStart.toEpochDay() + 1);
                MemberType memberType = resolveMemberType(rental.getRenter());

                rental.getItems().clear();
                List<EquipmentRentalItem> items = buildItems(rental, equipmentList, memberType, durationDays);
                rental.getItems().addAll(items);

                long subBase;
                if (subEquipmentEntries != null && !subEquipmentEntries.isEmpty()) {
                        rental.getSubItems().clear();
                        rentalRepository.save(rental);
                        subBase = buildAndSaveSubItems(rental, subEquipmentEntries, memberType, durationDays,
                                        programStart, programEnd, true);
                } else {
                        checkSubEquipmentQuantities(rental.getSubItems(), effectiveStart, effectiveEnd);
                        subBase = rental.getSubItems().stream().mapToLong(EquipmentRentalSubItem::getBaseAmount).sum();
                }
                long totalBase = items.stream().mapToLong(EquipmentRentalItem::getBaseAmount).sum() + subBase;
                rental.setTotalBaseAmount(totalBase);
                rental.setTotalPenaltyAmount(0L);
                rental.setTotalAmount(totalBase);
                rental.setDurationDays(durationDays);
                rental.setPickupDatetime(pickupDt);
                rental.setReturnDatetime(returnDt);
                rental.setStatus(RentalStatus.APPROVED);
                rental.setReviewedBy(committee);
                rental.setCommitteeNotes(committeeNotes);
                rental.setApprovedAt(LocalDateTime.now());

                rental = rentalRepository.save(rental);
                receiptService.createInvoice(rental);
                mailService.sendRentalApprovedToRenter(rental.getRenter().getEmail(), rental.getRentalNumber(),
                                rental.getTotalAmount(), programStart, programEnd, pickupDt, returnDt);
                return rental;
        }

        // ── Payment initiation ────────────────────────────────────────────────────

        @Transactional
        public EquipmentRental initiatePayment(Long id, String paymentMethod, String username) {
                EquipmentRental rental = findRental(id);
                User renter = findUser(username);

                if (!rental.getRenter().getId().equals(renter.getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }

                RentalPaymentMethod method;
                try {
                        method = RentalPaymentMethod.valueOf(paymentMethod.toUpperCase());
                } catch (IllegalArgumentException e) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Unsupported payment method: " + paymentMethod);
                }
                PaymentMethodHandler handler = handlerMap.get(method);
                if (handler == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Unsupported payment method: " + paymentMethod);
                }
                handler.initiate(rental, renter);
                return rentalRepository.save(rental);
        }

        // ── Cancel ────────────────────────────────────────────────────────────────

        @Transactional
        public EquipmentRental cancelRental(Long id, String username) {
                EquipmentRental rental = findRental(id);
                User renter = findUser(username);

                if (!rental.getRenter().getId().equals(renter.getId())) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }
                if (rental.getStatus() != RentalStatus.PENDING_REVIEW && rental.getStatus() != RentalStatus.APPROVED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Only PENDING REVIEW or APPROVED rentals can be cancelled");
                }
                rental.setStatus(RentalStatus.CANCELLED);
                return rentalRepository.save(rental);
        }

        // ── Committee lifecycle actions ───────────────────────────────────────────

        @Transactional
        public EquipmentRental markPickedUp(Long id, String username) {
                EquipmentRental rental = findRental(id);
                if (rental.getStatus() != RentalStatus.APPROVED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rental must be APPROVED to mark as picked up");
                }
                requireApproverOrFallback(rental, findUser(username));
                rental.setStatus(RentalStatus.PICKED_UP);
                rental.setPickedUpAt(LocalDateTime.now());
                rental = rentalRepository.save(rental);
                mailService.sendEquipmentPickedUpToRenter(rental.getRenter().getEmail(),
                                rental.getRentalNumber(), rental.getPickedUpAt());
                return rental;
        }

        @Transactional
        public EquipmentRental confirmManualPayment(Long id, String username) {
                EquipmentRental rental = findRental(id);
                boolean awaitingManualConfirmation = rental.getStatus() == RentalStatus.PENDING_PAYMENT
                                && (rental.getPaymentStatus() == RentalPaymentStatus.CASH_PENDING
                                                || rental.getPaymentStatus() == RentalPaymentStatus.BANK_TRANSFER_PENDING);
                if (!awaitingManualConfirmation) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rental is not awaiting manual payment confirmation");
                }
                User committee = findUser(username);
                paymentService.confirmCashPayment(rental, committee);
                return rentalRepository.save(rental);
        }

        @Transactional
        public EquipmentRental markActive(Long id) {
                EquipmentRental rental = findRental(id);
                if (rental.getStatus() != RentalStatus.PAID) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rental must be PAID before marking active");
                }
                rental.setStatus(RentalStatus.ACTIVE);
                rental.setActiveAt(LocalDateTime.now());
                return rentalRepository.save(rental);
        }

        @Transactional
        public EquipmentRental markReturned(Long id) {
                EquipmentRental rental = findRental(id);
                if (rental.getStatus() != RentalStatus.ACTIVE && rental.getStatus() != RentalStatus.OVERDUE) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Rental must be ACTIVE or OVERDUE to mark returned");
                }

                long overdueDays = rental.getReturnDatetime() != null
                                && LocalDateTime.now().isAfter(rental.getReturnDatetime())
                                                ? ChronoUnit.DAYS.between(rental.getReturnDatetime().toLocalDate(),
                                                                LocalDate.now())
                                                : 0L;

                applyPenalty(rental, overdueDays);
                rental.setStatus(RentalStatus.RETURNED);
                rental.setReturnedAt(LocalDateTime.now());
                rental = rentalRepository.save(rental);
                if (overdueDays > 0) {
                        receiptService.createOverdueInvoice(rental);
                }
                return rental;
        }

        // ── Logistics update (pickup/return datetimes) ────────────────────────────

        @Transactional
        public EquipmentRental updateLogistics(Long id, String username,
                        LocalDateTime pickupDatetime, LocalDateTime returnDatetime) {
                EquipmentRental rental = findRental(id);
                if (rental.getStatus() != RentalStatus.APPROVED && rental.getStatus() != RentalStatus.PICKED_UP) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Logistics can only be updated when rental is APPROVED or PICKED UP");
                }
                requireApproverOrFallback(rental, findUser(username));
                validateLogisticsBounds(pickupDatetime, returnDatetime,
                                rental.getProgramStartDate(), rental.getProgramEndDate());

                LocalDateTime effectiveStart = pickupDatetime.minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = returnDatetime.plusMinutes(bufferMinutes);
                List<MainEquipment> equipmentList = rental.getItems().stream()
                                .map(EquipmentRentalItem::getMainEquipment).toList();
                checkRentalConflicts(equipmentList, effectiveStart, effectiveEnd, id);
                checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

                rental.setPickupDatetime(pickupDatetime);
                rental.setReturnDatetime(returnDatetime);
                rental = rentalRepository.save(rental);
                mailService.sendLogisticsUpdatedToRenter(rental.getRenter().getEmail(),
                                rental.getRentalNumber(), pickupDatetime, returnDatetime);
                return rental;
        }

        // ── Equipment update (swap items, regenerate invoice) ─────────────────────

        @Transactional
        public EquipmentRental updateEquipment(Long id, String username,
                        List<Long> equipmentIds, List<SubEquipmentEntry> subEquipmentEntries) {
                EquipmentRental rental = findRental(id);
                if (rental.getStatus() != RentalStatus.APPROVED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Equipment can only be updated when rental is APPROVED");
                }
                requireApproverOrFallback(rental, findUser(username));

                List<MainEquipment> equipmentList = resolveAndValidateEquipment(equipmentIds);

                LocalDateTime effectiveStart = rental.getPickupDatetime().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = rental.getReturnDatetime().plusMinutes(bufferMinutes);
                checkRentalConflicts(equipmentList, effectiveStart, effectiveEnd, id);
                checkStatusConflicts(equipmentList, effectiveStart, effectiveEnd);

                int durationDays = rental.getDurationDays();
                MemberType memberType = resolveMemberType(rental.getRenter());

                rental.getItems().clear();
                List<EquipmentRentalItem> items = buildItems(rental, equipmentList, memberType, durationDays);
                rental.getItems().addAll(items);

                if (subEquipmentEntries != null && !subEquipmentEntries.isEmpty()) {
                        rental.getSubItems().clear();
                        buildAndSaveSubItems(rental, subEquipmentEntries, memberType, durationDays,
                                        rental.getProgramStartDate(), rental.getProgramEndDate(), true);
                }

                long subBase = rental.getSubItems().stream().mapToLong(EquipmentRentalSubItem::getBaseAmount).sum();
                long totalBase = items.stream().mapToLong(EquipmentRentalItem::getBaseAmount).sum() + subBase;
                rental.setTotalBaseAmount(totalBase);
                rental.setTotalAmount(totalBase);
                rental = rentalRepository.save(rental);

                receiptService.deleteInvoice(rental.getId());
                receiptService.createInvoice(rental);

                List<String> eqNames = new ArrayList<>();
                equipmentList.stream()
                                .map(e -> e.getBrand() + " " + e.getModel() + " (" + e.getSerialNumber() + ")")
                                .forEach(eqNames::add);
                rental.getSubItems().stream()
                                .map(s -> s.getSubEquipment().getType() + " " + s.getSubEquipment().getBrand()
                                                + " x" + s.getBorrowedQuantity())
                                .forEach(eqNames::add);
                mailService.sendEquipmentUpdatedToRenter(rental.getRenter().getEmail(),
                                rental.getRentalNumber(), eqNames);
                return rental;
        }

        // ── Queries ───────────────────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public List<EquipmentRental> getMyRentals(String username) {
                User renter = findUser(username);
                return rentalRepository.findByRenterOrderByCreatedAtDesc(renter);
        }

        @Transactional(readOnly = true)
        public List<EquipmentRental> getMyApprovals(String username) {
                User reviewer = findUser(username);
                return rentalRepository.findByReviewedByOrderByApprovedAtDesc(reviewer);
        }

        @Transactional(readOnly = true)
        public List<EquipmentRental> getAllRentals() {
                return rentalRepository.findAllByOrderByCreatedAtDesc();
        }

        @Transactional(readOnly = true)
        public List<EquipmentRental> getRentalsByEquipment(Long equipmentId) {
                return rentalRepository.findByEquipmentId(equipmentId);
        }

        @Transactional(readOnly = true)
        public List<EquipmentRental> getRentalsBySubEquipment(Long subEquipmentId) {
                return rentalRepository.findBySubEquipmentId(subEquipmentId);
        }

        @Transactional(readOnly = true)
        public Page<EquipmentRental> getAllRentals(String search, String status, int page, int size) {
                return getAllRentals(null, search, status, page, size);
        }

        @Transactional(readOnly = true)
        public Page<EquipmentRental> getAllRentals(Long equipmentId, String search, String status, int page, int size) {
                int clampedPage = Math.max(page, 0);
                int clampedSize = Math.min(Math.max(size, 1), 100);
                Pageable pageable = PageRequest.of(clampedPage, clampedSize);
                String s = (search == null) ? "" : search.trim();
                String st = (status == null) ? "" : status.trim();
                return equipmentId != null
                                ? rentalRepository.searchRentalsByEquipment(equipmentId, s, st, pageable)
                                : rentalRepository.searchRentals(s, st, pageable);
        }

        // ── Overdue penalty (called by scheduler + markReturned) ─────────────────

        @Transactional
        public void updateOverduePenalties() {
                LocalDateTime now = LocalDateTime.now();
                LocalDate today = LocalDate.now();

                List<EquipmentRental> newlyOverdue = rentalRepository
                                .findByStatusAndReturnDatetimeBefore(RentalStatus.ACTIVE, now);
                newlyOverdue.forEach(r -> r.setStatus(RentalStatus.OVERDUE));
                rentalRepository.saveAll(newlyOverdue);

                List<EquipmentRental> alreadyOverdue = rentalRepository.findByStatus(RentalStatus.OVERDUE);
                for (EquipmentRental r : alreadyOverdue) {
                        long days = ChronoUnit.DAYS.between(r.getReturnDatetime().toLocalDate(), today);
                        applyPenalty(r, days);
                }
                rentalRepository.saveAll(alreadyOverdue);

                log.info("Marked {} new OVERDUE, recalculated penalty for {} existing OVERDUE rental(s)",
                                newlyOverdue.size(), alreadyOverdue.size());
        }

        // ── Equipment schedules ───────────────────────────────────────────────────

        @Transactional(readOnly = true)
        public EquipmentSchedulesResponse getEquipmentSchedules(
                        Long rentalId,
                        List<Long> mainEquipmentIds,
                        List<Long> subEquipmentIds,
                        String username) {
                EquipmentRental rental = findRental(rentalId);
                User caller = findUser(username);

                boolean isCommittee = caller.getRoles().stream()
                                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
                boolean isOwner = rental.getRenter().getId().equals(caller.getId());
                if (!isCommittee && !isOwner) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
                }

                List<Long> mainIds = (mainEquipmentIds != null && !mainEquipmentIds.isEmpty())
                                ? mainEquipmentIds
                                : rental.getItems().stream()
                                                .map(i -> i.getMainEquipment().getMainEquipmentId()).toList();
                List<Long> subIds = (subEquipmentIds != null && !subEquipmentIds.isEmpty())
                                ? subEquipmentIds
                                : rental.getSubItems().stream()
                                                .map(s -> s.getSubEquipment().getSubEquipmentId()).toList();

                Map<Long, List<MainEquipmentStatusResponse>> statusMap =
                        mainIds.isEmpty() ? Map.of()
                                : mainEquipmentStatusRepository.findAllByEquipmentIds(mainIds)
                                                .stream()
                                                .collect(Collectors.groupingBy(
                                                                s -> s.getMainEquipment().getMainEquipmentId(),
                                                                Collectors.mapping(
                                                                                s -> new MainEquipmentStatusResponse(
                                                                                                s.getId(), s.getStatusType(),
                                                                                                s.getStartDatetime(), s.getEndDatetime(), s.getNotes()),
                                                                                Collectors.toList())));

                Map<Long, List<SubEquipmentQuantityHoldResponse>> holdMap =
                        subIds.isEmpty() ? Map.of()
                                : subEquipmentQuantityHoldRepository.findAllBySubEquipmentIds(subIds)
                                                .stream()
                                                .collect(Collectors.groupingBy(
                                                                h -> h.getSubEquipment().getSubEquipmentId(),
                                                                Collectors.mapping(
                                                                                h -> new SubEquipmentQuantityHoldResponse(
                                                                                                h.getId(), h.getQuantity(),
                                                                                                h.getStartDatetime(), h.getEndDatetime(), h.getNotes()),
                                                                                Collectors.toList())));

                List<MainEquipmentScheduleEntry> mainEntries =
                        mainIds.stream()
                                .map(id -> {
                                        MainEquipment eq = mainEquipmentRepository.findById(id)
                                                        .orElseThrow(() -> new ResponseStatusException(
                                                                        HttpStatus.NOT_FOUND, "Equipment not found: " + id));
                                        return new MainEquipmentScheduleEntry(
                                                        eq.getMainEquipmentId(), eq.getBrand(), eq.getModel(),
                                                        eq.getSerialNumber(),
                                                        statusMap.getOrDefault(id, List.of()));
                                }).toList();

                List<SubEquipmentScheduleEntry> subEntries =
                        subIds.stream()
                                .map(id -> {
                                        SubEquipment sub = subEquipmentRepository.findById(id)
                                                        .orElseThrow(() -> new ResponseStatusException(
                                                                        HttpStatus.NOT_FOUND, "Sub-equipment not found: " + id));
                                        int qty = rental.getSubItems().stream()
                                                        .filter(s -> s.getSubEquipment().getSubEquipmentId().equals(id))
                                                        .mapToInt(EquipmentRentalSubItem::getBorrowedQuantity).sum();
                                        return new SubEquipmentScheduleEntry(
                                                        sub.getSubEquipmentId(), sub.getType(), sub.getBrand(),
                                                        sub.getCameraModel(), qty,
                                                        holdMap.getOrDefault(id, List.of()));
                                }).toList();

                return new EquipmentSchedulesResponse(mainEntries, subEntries);
        }

        // ── Private helpers ───────────────────────────────────────────────────────

        private void checkRentalConflicts(List<MainEquipment> equipmentList,
                        LocalDateTime effectiveStart, LocalDateTime effectiveEnd, Long excludeRentalId) {
                List<String> rentalConflicts = equipmentList.stream()
                                .filter(eq -> rentalItemRepository.existsConflictingApprovedRental(
                                                eq.getMainEquipmentId(), effectiveStart, effectiveEnd, excludeRentalId,
                                                BLOCKING_STATUSES))
                                .map(eq -> eq.getBrand() + " " + eq.getModel()
                                                + (eq.getSerialNumber() != null ? " (" + eq.getSerialNumber() + ")"
                                                                : ""))
                                .toList();
                if (!rentalConflicts.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "The following equipment conflicts with an existing approved rental: "
                                                        + String.join(", ", rentalConflicts));
                }
                List<String> eventConflicts = equipmentList.stream()
                                .filter(eq -> requestEventItemRepository.existsConflictingRequest(
                                                eq.getMainEquipmentId(), 0L, EVENT_BLOCKING_STATUSES, effectiveStart,
                                                effectiveEnd))
                                .map(eq -> eq.getBrand() + " " + eq.getModel()
                                                + (eq.getSerialNumber() != null ? " (" + eq.getSerialNumber() + ")"
                                                                : ""))
                                .toList();
                if (!eventConflicts.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "The following equipment conflicts with an existing approved event request: "
                                                        + String.join(", ", eventConflicts));
                }
        }

        private void checkStatusConflicts(List<MainEquipment> equipmentList,
                        LocalDateTime effectiveStart, LocalDateTime effectiveEnd) {
                List<String> conflicts = equipmentList.stream()
                                .filter(eq -> mainEquipmentStatusRepository.existsConflictingStatus(
                                                eq.getMainEquipmentId(), effectiveStart, effectiveEnd))
                                .map(eq -> eq.getBrand() + " " + eq.getModel())
                                .toList();
                if (!conflicts.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "The following equipment is under Kelab Fotokreatif's usage for the requested period: "
                                                        + String.join(", ", conflicts));
                }
        }

        private void checkStatusConflictsInterior(List<MainEquipment> equipmentList,
                        LocalDate startDate, LocalDate endDate) {
                List<String> conflicts = equipmentList.stream()
                                .filter(eq -> mainEquipmentStatusRepository.existsInteriorConflictingStatus(
                                                eq.getMainEquipmentId(), startDate, endDate))
                                .map(eq -> eq.getBrand() + " " + eq.getModel()
                                                + (eq.getSerialNumber() != null ? " (" + eq.getSerialNumber() + ")"
                                                                : ""))
                                .toList();
                if (!conflicts.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "The following equipment is under Kelab Fotokreatif's usage for the requested period: "
                                                        + String.join(", ", conflicts));
                }
        }

        private void checkSubEquipmentQuantities(List<EquipmentRentalSubItem> subItems,
                        LocalDateTime effectiveStart, LocalDateTime effectiveEnd) {
                for (EquipmentRentalSubItem subItem : subItems) {
                        SubEquipment sub = subItem.getSubEquipment();
                        int committed = rentalSubItemRepository.sumCommittedQuantity(
                                        sub.getSubEquipmentId(), effectiveStart, effectiveEnd, BLOCKING_STATUSES);
                        int held = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                                        sub.getSubEquipmentId(), effectiveStart, effectiveEnd, 0L);
                        if (committed + held + subItem.getBorrowedQuantity() > sub.getTotalQuantity()) {
                                throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                "Insufficient quantity for '" + sub.getType() + " " + sub.getBrand()
                                                                + "': requested " + subItem.getBorrowedQuantity()
                                                                + ", available "
                                                                + (sub.getTotalQuantity() - committed - held));
                        }
                }
        }

        private void validateLogisticsBounds(LocalDateTime pickupDt, LocalDateTime returnDt,
                        LocalDate programStart, LocalDate programEnd) {
                if (pickupDt.toLocalDate().isAfter(programStart)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Pickup Date & Time cannot be after Program Start Date (" + programStart + ")");
                }
                if (returnDt.toLocalDate().isBefore(programEnd)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Return Date & Time cannot be before Program End Date (" + programEnd + ")");
                }
        }

        private void requireApproverOrFallback(EquipmentRental rental, User requester) {
                User reviewer = rental.getReviewedBy();
                boolean isOriginalApprover = reviewer != null && reviewer.getId().equals(requester.getId());
                boolean approverStillActive = reviewer != null && reviewer.isActive()
                                && reviewer.getRoles().stream()
                                                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
                if (!isOriginalApprover && approverStillActive) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "Only the approving Equipment Committee member may perform this action");
                }
        }

        private void applyPenalty(EquipmentRental rental, long overdueDays) {
                long total = 0L;
                for (EquipmentRentalItem item : rental.getItems()) {
                        long penalty = item.getLatePenaltyPerDay() * overdueDays;
                        item.setLatePenaltyAmount(penalty);
                        item.setItemTotalAmount(item.getBaseAmount() + penalty);
                        total += penalty;
                }
                for (EquipmentRentalSubItem sub : rental.getSubItems()) {
                        long penalty = sub.getLatePenaltyPerDay() * overdueDays;
                        sub.setLatePenaltyAmount(penalty);
                        sub.setItemTotalAmount(sub.getBaseAmount() + penalty);
                        total += penalty;
                }
                rental.setTotalPenaltyAmount(total);
                rental.setTotalAmount(rental.getTotalBaseAmount() + total);
        }

        private User findUser(String username) {
                return userRepository.findByUsername(username)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "User not found: " + username));
        }

        private EquipmentRental findRental(Long id) {
                return rentalRepository.findById(id)
                                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "Rental not found: " + id));
        }

        private List<MainEquipment> resolveAndValidateEquipment(List<Long> ids) {
                if (ids == null || ids.isEmpty()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "At least one equipment item is required");
                }
                List<MainEquipment> list = new ArrayList<>();
                for (Long eqId : ids) {
                        MainEquipment eq = mainEquipmentRepository.findById(eqId)
                                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                        "Equipment not found: " + eqId));
                        if (!eq.isForRent()) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Equipment '" + eq.getSerialNumber() + "' is not available for rent");
                        }
                        list.add(eq);
                }
                return list;
        }

        private MemberType resolveMemberType(User user) {
                boolean isStudent = user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_STUDENT"));
                if (isStudent)
                        return MemberType.STUDENT;
                boolean isNonStudent = user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_NON_STUDENT"));
                if (isNonStudent)
                        return MemberType.NON_STUDENT;
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "User is not a STUDENT nor NON_STUDENT: " + user.getUsername());
        }

        private List<EquipmentRentalItem> buildItems(EquipmentRental rental, List<MainEquipment> equipmentList,
                        MemberType memberType, int durationDays) {
                List<EquipmentRentalItem> items = new ArrayList<>();
                for (MainEquipment eq : equipmentList) {
                        if (eq.getPricingCategory() == null) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Equipment '" + eq.getSerialNumber()
                                                                + "' has no pricing category assigned");
                        }
                        RentalPricingCategory cat = eq.getPricingCategory().getName();
                        RentalPricing pricing = pricingRepository
                                        .findByPricingCategory_NameAndMemberType(cat, memberType)
                                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                        "No pricing found for category " + cat + " and member type "
                                                                        + memberType));

                        long latePenalty = toC(pricing.getLatePenaltyPerDay());
                        long baseAmount = toC(rentalPricingService.calculateCost(pricing, durationDays));

                        items.add(EquipmentRentalItem.builder()
                                        .equipmentRental(rental)
                                        .mainEquipment(eq)
                                        .latePenaltyPerDay(latePenalty)
                                        .baseAmount(baseAmount)
                                        .latePenaltyAmount(0L)
                                        .itemTotalAmount(baseAmount)
                                        .build());
                }
                return rentalItemRepository.saveAll(items);
        }

        private long toC(BigDecimal rm) {
                return rm.multiply(BigDecimal.valueOf(100)).longValue();
        }

        private long buildAndSaveSubItems(EquipmentRental rental, List<SubEquipmentEntry> entries,
                        MemberType memberType, int durationDays, LocalDate startDate, LocalDate endDate,
                        boolean checkQuantity) {
                List<EquipmentRentalSubItem> subItems = new ArrayList<>();
                long subTotal = 0L;
                LocalDateTime effectiveStart = startDate.atStartOfDay().minusMinutes(bufferMinutes);
                LocalDateTime effectiveEnd = endDate.atTime(23, 59, 59).plusMinutes(bufferMinutes);
                for (SubEquipmentEntry entry : entries) {
                        SubEquipment sub = subEquipmentRepository.findById(entry.subEquipmentId())
                                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                        "Sub-equipment not found: " + entry.subEquipmentId()));
                        if (entry.quantity() <= 0) {
                                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                                "Quantity must be at least 1 for sub-equipment: "
                                                                + entry.subEquipmentId());
                        }
                        int held = subEquipmentQuantityHoldRepository.sumHeldQuantity(
                                        sub.getSubEquipmentId(), effectiveStart, effectiveEnd, 0L);
                        if (checkQuantity) {
                                // Strict check at update time: use total committed in the exact window
                                int committed = rentalSubItemRepository.sumCommittedQuantity(
                                                sub.getSubEquipmentId(), effectiveStart, effectiveEnd,
                                                BLOCKING_STATUSES);
                                if (committed + held + entry.quantity() > sub.getTotalQuantity()) {
                                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                        "Insufficient quantity for '" + sub.getType() + " "
                                                                        + sub.getBrand()
                                                                        + "' for the requested dates: requested "
                                                                        + entry.quantity()
                                                                        + ", available "
                                                                        + (sub.getTotalQuantity() - committed - held));
                                }
                        } else {
                                // Boundary-aware check at submit time:
                                // The committee can choose a window that avoids either the start-boundary
                                // rentals
                                // (returning on startDate) or the end-boundary rentals (picking up on endDate).
                                // minConcurrent = totalCommitted - max(startBoundaryQty, endBoundaryQty)
                                int totalCommitted = rentalSubItemRepository.sumCommittedQuantity(
                                                sub.getSubEquipmentId(), effectiveStart, effectiveEnd,
                                                BLOCKING_STATUSES);
                                int feasibleStartBoundaryQty = rentalSubItemRepository
                                                .findBoundaryReturnQuantities(List.of(sub.getSubEquipmentId()),
                                                                startDate.atStartOfDay(), BLOCKING_STATUSES, 0L)
                                                .stream()
                                                .filter(row -> !row.datetime().plusMinutes(bufferMinutes)
                                                                .toLocalDate().isAfter(startDate))
                                                .mapToInt(row -> row.quantity().intValue()).sum();
                                int feasibleEndBoundaryQty = rentalSubItemRepository
                                                .findBoundaryPickupQuantities(List.of(sub.getSubEquipmentId()),
                                                                endDate.atStartOfDay(), BLOCKING_STATUSES, 0L)
                                                .stream()
                                                .filter(row -> !row.datetime().minusMinutes(bufferMinutes)
                                                                .toLocalDate().isBefore(endDate))
                                                .mapToInt(row -> row.quantity().intValue()).sum();
                                int minConcurrent = Math.max(0, totalCommitted
                                                - Math.max(feasibleStartBoundaryQty, feasibleEndBoundaryQty));
                                if (minConcurrent + held + entry.quantity() > sub.getTotalQuantity()) {
                                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                        "Insufficient quantity for '" + sub.getType() + " "
                                                                        + sub.getBrand()
                                                                        + "' for the requested dates: requested "
                                                                        + entry.quantity()
                                                                        + ", available " + (sub.getTotalQuantity()
                                                                                        - minConcurrent - held));
                                }
                        }
                        long latePenalty = 0L;
                        long baseAmount = 0L;
                        if (sub.getPricingCategory() != null) {
                                RentalPricingCategory cat = sub.getPricingCategory().getName();
                                RentalPricing pricing = pricingRepository
                                                .findByPricingCategory_NameAndMemberType(cat, memberType)
                                                .orElse(null);
                                if (pricing != null) {
                                        latePenalty = toC(pricing.getLatePenaltyPerDay()) * entry.quantity();
                                        baseAmount = toC(rentalPricingService.calculateCost(pricing, durationDays))
                                                        * entry.quantity();
                                }
                        }
                        subTotal += baseAmount;

                        subItems.add(EquipmentRentalSubItem.builder()
                                        .equipmentRental(rental)
                                        .subEquipment(sub)
                                        .borrowedQuantity(entry.quantity())
                                        .latePenaltyPerDay(latePenalty)
                                        .baseAmount(baseAmount)
                                        .latePenaltyAmount(0L)
                                        .itemTotalAmount(baseAmount)
                                        .build());
                }
                rentalSubItemRepository.saveAll(subItems);
                rental.getSubItems().addAll(subItems);
                return subTotal;
        }

}
