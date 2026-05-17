package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.*;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.*;
import com.ifoto.ifoto_backend.service.payment.PaymentMethodHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MainEquipmentRepository mainEquipmentRepository;
    private final RentalPricingRepository pricingRepository;
    private final RentalPricingService rentalPricingService;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final MailService mailService;
    private final List<PaymentMethodHandler> paymentHandlers;

    private Map<RentalPaymentMethod, PaymentMethodHandler> handlerMap;

    @PostConstruct
    void initHandlerMap() {
        handlerMap = paymentHandlers.stream()
                .collect(Collectors.toMap(PaymentMethodHandler::getMethod, h -> h));
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Transactional
    public EquipmentRental submitRental(List<Long> equipmentIds, LocalDate startDate, LocalDate endDate,
            String renterNotes, String username) {
        User renter = findUser(username);
        MemberType memberType = resolveMemberType(renter);

        List<MainEquipment> equipmentList = resolveAndValidateEquipment(equipmentIds);

        // Block on conflict with already-approved/active rentals
        List<RentalStatus> blockingStatuses = List.of(
                RentalStatus.APPROVED, RentalStatus.PENDING_PAYMENT,
                RentalStatus.PAID, RentalStatus.ACTIVE, RentalStatus.OVERDUE);
        for (MainEquipment eq : equipmentList) {
            if (rentalItemRepository.existsConflictingApprovedRental(
                    eq.getMainEquipmentId(), startDate, endDate, 0L, blockingStatuses)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment '" + eq.getBrand() + " " + eq.getModel() + " (" + eq.getSerialNumber()
                                + ")' is already booked for the requested dates");
            }
        }

        EquipmentRental rental = EquipmentRental.builder()
                .renter(renter)
                .status(RentalStatus.PENDING_REVIEW)
                .requestedStartDate(startDate)
                .requestedEndDate(endDate)
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

        List<String> committeeEmails = userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")
                .stream().map(User::getEmail).toList();
        if (!committeeEmails.isEmpty()) {
            List<String> eqNames = equipmentList.stream()
                    .map(e -> e.getBrand() + " " + e.getModel() + " (" + e.getSerialNumber() + ")")
                    .toList();
            mailService.sendRentalSubmittedToCommittee(committeeEmails, rental.getRentalNumber(),
                    renter.getFullName() != null ? renter.getFullName() : renter.getUsername(),
                    eqNames, startDate, endDate);
        }
        return rental;
    }

    // ── Review (approve / reject) ─────────────────────────────────────────────

    @Transactional
    public EquipmentRental reviewRental(Long id, String action, LocalDate approvedStart,
            LocalDate approvedEnd, List<Long> equipmentIds, String rejectionReason,
            String committeeNotes, String username) {
        User committee = findUser(username);
        EquipmentRental rental = findRental(id);

        if (rental.getStatus() != RentalStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rental is not in PENDING_REVIEW status");
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

        if (approvedStart == null || approvedEnd == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "approvedStartDate and approvedEndDate required for approval");
        }

        List<Long> finalEquipmentIds = (equipmentIds != null && !equipmentIds.isEmpty())
                ? equipmentIds
                : rental.getItems().stream().map(i -> i.getMainEquipment().getMainEquipmentId()).toList();
        List<MainEquipment> equipmentList = resolveAndValidateEquipment(finalEquipmentIds);

        // Hard availability check — approved dates only, exclude self
        List<RentalStatus> blockingStatuses = List.of(
                RentalStatus.APPROVED, RentalStatus.PENDING_PAYMENT,
                RentalStatus.PAID, RentalStatus.ACTIVE, RentalStatus.OVERDUE);
        for (MainEquipment eq : equipmentList) {
            if (rentalItemRepository.existsConflictingApprovedRental(
                    eq.getMainEquipmentId(), approvedStart, approvedEnd, id, blockingStatuses)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Equipment '" + eq.getBrand() + " " + eq.getModel() + " (" + eq.getSerialNumber()
                                + ")' conflicts with an existing approved rental");
            }
        }

        int durationDays = (int) (approvedEnd.toEpochDay() - approvedStart.toEpochDay() + 1);
        MemberType memberType = resolveMemberType(rental.getRenter());

        // Replace items
        rental.getItems().clear();
        List<EquipmentRentalItem> items = buildItems(rental, equipmentList, memberType, durationDays);
        rental.getItems().addAll(items);

        long totalBase = items.stream().mapToLong(EquipmentRentalItem::getBaseAmount).sum();
        rental.setTotalBaseAmount(totalBase);
        rental.setTotalPenaltyAmount(0L);
        rental.setTotalAmount(totalBase);
        rental.setDurationDays(durationDays);
        rental.setApprovedStartDate(approvedStart);
        rental.setApprovedEndDate(approvedEnd);
        rental.setDueReturnDate(approvedEnd);
        rental.setStatus(RentalStatus.APPROVED);
        rental.setReviewedBy(committee);
        rental.setCommitteeNotes(committeeNotes);
        rental.setApprovedAt(LocalDateTime.now());

        rental = rentalRepository.save(rental);
        mailService.sendRentalApprovedToRenter(rental.getRenter().getEmail(), rental.getRentalNumber(),
                rental.getTotalAmount(), approvedStart, approvedEnd);
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method: " + paymentMethod);
        }
        PaymentMethodHandler handler = handlerMap.get(method);
        if (handler == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method: " + paymentMethod);
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
        if (rental.getStatus() != RentalStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING_REVIEW rentals can be cancelled");
        }
        rental.setStatus(RentalStatus.CANCELLED);
        return rentalRepository.save(rental);
    }

    // ── Committee lifecycle actions ───────────────────────────────────────────

    @Transactional
    public EquipmentRental confirmCash(Long id, String username) {
        EquipmentRental rental = findRental(id);
        if (rental.getStatus() != RentalStatus.PENDING_PAYMENT || rental.getPaymentStatus() != RentalPaymentStatus.CASH_PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rental is not in PENDING_PAYMENT status with CASH payment method");
        }
        User committee = findUser(username);
        paymentService.confirmCashPayment(rental, committee);
        return rentalRepository.save(rental);
    }

    @Transactional
    public EquipmentRental markActive(Long id) {
        EquipmentRental rental = findRental(id);
        if (rental.getStatus() != RentalStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rental must be PAID before marking active");
        }
        rental.setStatus(RentalStatus.ACTIVE);
        rental.setActiveAt(LocalDateTime.now());
        return rentalRepository.save(rental);
    }

    @Transactional
    public EquipmentRental markReturned(Long id) {
        EquipmentRental rental = findRental(id);
        if (rental.getStatus() != RentalStatus.ACTIVE && rental.getStatus() != RentalStatus.OVERDUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rental must be ACTIVE or OVERDUE to mark returned");
        }

        LocalDate today = LocalDate.now();
        long overdueDays = rental.getDueReturnDate() != null && today.isAfter(rental.getDueReturnDate())
                ? today.toEpochDay() - rental.getDueReturnDate().toEpochDay()
                : 0L;

        long totalPenalty = 0L;
        for (EquipmentRentalItem item : rental.getItems()) {
            long penalty = item.getLatePenaltyPerDay() * overdueDays;
            item.setLatePenaltyAmount(penalty);
            item.setItemTotalAmount(item.getBaseAmount() + penalty);
            totalPenalty += penalty;
        }

        rental.setTotalPenaltyAmount(totalPenalty);
        rental.setTotalAmount(rental.getTotalBaseAmount() + totalPenalty);
        rental.setStatus(RentalStatus.RETURNED);
        rental.setReturnedAt(LocalDateTime.now());
        return rentalRepository.save(rental);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EquipmentRental> getMyRentals(String username) {
        User renter = findUser(username);
        return rentalRepository.findByRenterOrderByCreatedAtDesc(renter);
    }

    @Transactional(readOnly = true)
    public EquipmentRental getRental(Long id, String username) {
        EquipmentRental rental = findRental(id);
        User requester = findUser(username);
        boolean isOwner = rental.getRenter().getId().equals(requester.getId());
        boolean isCommittee = requester.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
        if (!isOwner && !isCommittee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return rental;
    }

    @Transactional(readOnly = true)
    public List<EquipmentRental> getAllRentals() {
        return rentalRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<EquipmentRental> getAllRentals(String search, String status, int page, int size) {
        int clampedPage = Math.max(page, 0);
        int clampedSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(clampedPage, clampedSize);
        String s = (search == null) ? "" : search.trim();
        String st = (status == null) ? "" : status.trim();
        return rentalRepository.searchRentals(s, st, pageable);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
    }

    private EquipmentRental findRental(Long id) {
        return rentalRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found: " + id));
    }

    private List<MainEquipment> resolveAndValidateEquipment(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one equipment item is required");
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
        if (isStudent) return MemberType.STUDENT;
        boolean isNonStudent = user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_NON_STUDENT"));
        if (isNonStudent) return MemberType.NON_STUDENT;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "User does not have STUDENT or NON_STUDENT role");
    }

    private List<EquipmentRentalItem> buildItems(EquipmentRental rental, List<MainEquipment> equipmentList,
            MemberType memberType, int durationDays) {
        List<EquipmentRentalItem> items = new ArrayList<>();
        for (MainEquipment eq : equipmentList) {
            if (eq.getPricingCategory() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Equipment '" + eq.getSerialNumber() + "' has no pricing category assigned");
            }
            RentalPricingCategory cat = eq.getPricingCategory().getName();
            RentalPricing pricing = pricingRepository.findByPricingCategory_NameAndMemberType(cat, memberType)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No pricing found for category " + cat + " and member type " + memberType));

            long rate1Day = toC(pricing.getRate1Day());
            long rate3Days = toC(pricing.getRate3Days());
            long rateExtra = toC(pricing.getRatePerDayExtra());
            long latePenalty = toC(pricing.getLatePenaltyPerDay());
            long baseAmount = toC(rentalPricingService.calculateCost(pricing, durationDays));

            items.add(EquipmentRentalItem.builder()
                    .equipmentRental(rental)
                    .mainEquipment(eq)
                    .memberType(memberType)
                    .pricingCategory(cat.name())
                    .rate1Day(rate1Day)
                    .rate3Days(rate3Days)
                    .ratePerDayExtra(rateExtra)
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

}
