package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.*;
import com.ifoto.ifoto_backend.dto.EquipmentRentalDTO.SubEquipmentEntry;
import com.ifoto.ifoto_backend.model.*;
import com.ifoto.ifoto_backend.model.enumerator.*;
import com.ifoto.ifoto_backend.repository.*;
import com.ifoto.ifoto_backend.service.*;
import com.ifoto.ifoto_backend.service.payment.PaymentMethodHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentRentalServiceTest {

    @Mock
    private EquipmentRentalRepository rentalRepository;
    @Mock
    private EquipmentRentalItemRepository rentalItemRepository;
    @Mock
    private EquipmentRentalSubItemRepository rentalSubItemRepository;
    @Mock
    private SubEquipmentRepository subEquipmentRepository;
    @Mock
    private MainEquipmentRepository mainEquipmentRepository;
    @Mock
    private MainEquipmentStatusRepository mainEquipmentStatusRepository;
    @Mock
    private SubEquipmentQuantityHoldRepository subEquipmentQuantityHoldRepository;
    @Mock
    private RentalPricingRepository pricingRepository;
    @Mock
    private RentalPricingService rentalPricingService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentService paymentService;
    @Mock
    private ReceiptService receiptService;
    @Mock
    private MailService mailService;
    @Mock
    private List<PaymentMethodHandler> paymentHandlers;
    @Mock
    private EventEquipmentRequestItemRepository requestEventItemRepository;

    @InjectMocks
    private EquipmentRentalService service;

    private static final Long RENTAL_ID = 1L;

    // ── markActive ────────────────────────────────────────────────────────────

    @Test
    void markActive_rentalIsPaid_transitionsToActive() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PAID);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.markActive(RENTAL_ID);

        assertEquals(RentalStatus.ACTIVE, result.getStatus());
    }

    @Test
    void markActive_rentalNotPaid_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PENDING_REVIEW);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markActive(RENTAL_ID));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ── markReturned ──────────────────────────────────────────────────────────

    @Test
    void markReturned_rentalIsActive_noOverdue_transitionsToReturned() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.ACTIVE);
        rental.setReturnDatetime(LocalDateTime.now().plusDays(5)); // not yet overdue
        rental.setTotalBaseAmount(5000L);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.markReturned(RENTAL_ID);

        assertEquals(RentalStatus.RETURNED, result.getStatus());
        verify(receiptService, never()).createOverdueInvoice(any());
    }

    @Test
    void markReturned_rentalIsOverdue_calculatesPenaltyAndCreatesOverdueInvoice() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.OVERDUE);
        rental.setReturnDatetime(LocalDateTime.now().minusDays(2));
        rental.setTotalBaseAmount(10000L);

        EquipmentRentalItem item = EquipmentRentalItem.builder()
                .latePenaltyPerDay(500L)
                .baseAmount(10000L)
                .itemTotalAmount(10000L)
                .build();
        rental.setItems(new ArrayList<>(List.of(item)));
        rental.setSubItems(new ArrayList<>());

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.markReturned(RENTAL_ID);

        assertEquals(RentalStatus.RETURNED, result.getStatus());
        assertEquals(1000L, result.getTotalPenaltyAmount()); // 500 * 2 days
        verify(receiptService).createOverdueInvoice(result);
    }

    @Test
    void markReturned_invalidStatus_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PAID);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markReturned(RENTAL_ID));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ── cancelRental ─────────────────────────────────────────────────────────

    @Test
    void cancelRental_pendingReviewStatus_cancelsSuccessfully() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.PENDING_REVIEW, renter);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.cancelRental(RENTAL_ID, "alice");

        assertEquals(RentalStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelRental_approvedStatus_cancelsSuccessfully() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.cancelRental(RENTAL_ID, "alice");

        assertEquals(RentalStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelRental_paidStatus_throwsBadRequest() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.PAID, renter);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancelRental(RENTAL_ID, "alice"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void cancelRental_differentUser_throwsForbidden() {
        User renter = userWith(1L, "alice");
        User otherUser = userWith(2L, "bob");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.PENDING_REVIEW, renter);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(otherUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cancelRental(RENTAL_ID, "bob"));
        assertEquals(403, ex.getStatusCode().value());
    }

    // ── confirmManualPayment ──────────────────────────────────────────────────

    @Test
    void confirmManualPayment_cashPending_succeeds() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PENDING_PAYMENT);
        rental.setPaymentStatus(RentalPaymentStatus.CASH_PENDING);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.confirmManualPayment(RENTAL_ID, "committee"));
    }

    @Test
    void confirmManualPayment_bankTransferPending_succeeds() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PENDING_PAYMENT);
        rental.setPaymentStatus(RentalPaymentStatus.BANK_TRANSFER_PENDING);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.confirmManualPayment(RENTAL_ID, "committee"));
    }

    @Test
    void confirmManualPayment_notAwaitingManual_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.APPROVED);
        rental.setPaymentStatus(RentalPaymentStatus.NONE);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmManualPayment(RENTAL_ID, "committee"));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ── markPickedUp ──────────────────────────────────────────────────────────

    @Test
    void markPickedUp_notApproved_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PAID);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.markPickedUp(RENTAL_ID, "committee"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void markPickedUp_approved_noReviewer_transitionsToPickedUp() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.APPROVED);
        rental.setReviewedBy(null); // no reviewer — anyone can mark as picked up
        User committee = userWith(10L, "committee");
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(committee));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(mailService).sendEquipmentPickedUpToRenter(any(), any(), any());

        // Need renter email for mail notification
        rental.setRenter(userWith(99L, "renter"));

        EquipmentRental result = service.markPickedUp(RENTAL_ID, "committee");

        assertEquals(RentalStatus.PICKED_UP, result.getStatus());
    }

    // ── submitRental ──────────────────────────────────────────────────────────

    @Test
    void submitRental_validRequest_noSubItems_returnsPendingReview() {
        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        MainEquipment equipment = equipmentWith(10L, true, RentalPricingCategory.CAMERA);
        RentalPricing pricing = pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(mainEquipmentStatusRepository.existsInteriorConflictingStatus(any(), any(), any())).thenReturn(false);
        when(rentalRepository.save(any())).thenAnswer(inv -> {
            EquipmentRental r = inv.getArgument(0);
            if (r.getId() == null)
                r.setId(100L);
            return r;
        });
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any())).thenReturn(Optional.of(pricing));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of());

        EquipmentRental result = service.submitRental(
                List.of(10L),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                "test notes", "alice", null);

        assertEquals(RentalStatus.PENDING_REVIEW, result.getStatus());
        assertNotNull(result.getRentalNumber());
        assertTrue(result.getRentalNumber().startsWith("RNT-"));
        assertEquals(renter, result.getRenter());
    }

    @Test
    void submitRental_userNotFound_throwsNotFound() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(1L), LocalDate.now(), LocalDate.now().plusDays(1), null, "unknown",
                        null));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void submitRental_emptyEquipmentIds_throwsBadRequest() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithRole(1L, "alice", "ROLE_STUDENT")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(), LocalDate.now(), LocalDate.now().plusDays(1), null, "alice",
                        null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitRental_equipmentNotFound_throwsNotFound() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithRole(1L, "alice", "ROLE_STUDENT")));
        when(mainEquipmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(99L), LocalDate.now(), LocalDate.now().plusDays(1), null, "alice",
                        null));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void submitRental_equipmentNotForRent_throwsBadRequest() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithRole(1L, "alice", "ROLE_STUDENT")));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipmentWith(10L, false, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L), LocalDate.now(), LocalDate.now().plusDays(1), null, "alice",
                        null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitRental_equipmentNoPricingCategory_throwsBadRequest() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithRole(1L, "alice", "ROLE_STUDENT")));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipmentWith(10L, true, null)));
        when(mainEquipmentStatusRepository.existsInteriorConflictingStatus(any(), any(), any())).thenReturn(false);
        when(rentalRepository.save(any())).thenAnswer(inv -> {
            EquipmentRental r = inv.getArgument(0);
            if (r.getId() == null)
                r.setId(100L);
            return r;
        });

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null,
                        "alice", null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitRental_statusConflict_throwsConflict() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(userWithRole(1L, "alice", "ROLE_STUDENT")));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(mainEquipmentStatusRepository.existsInteriorConflictingStatus(any(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null,
                        "alice", null));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void submitRental_userWithNoValidRole_throwsBadRequest() {
        User renter = User.builder().id(1L).username("alice").email("alice@test.com").roles(new HashSet<>()).build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null,
                        "alice", null));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ── submitRental – committee email notification ───────────────────────────

    @Test
    void submitRental_committeeExists_sendsEmailToCommitteeAddresses() {
        User committeeUser = userWith(50L, "committee");
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);

        setupSubmitRentalBaseMocks("alice", 10L);
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        service.submitRental(List.of(10L), start, end, null, "alice", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> emailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(mailService).sendRentalSubmittedToCommittee(
                emailsCaptor.capture(), any(), any(), any(), any(), eq(start), eq(end));
        assertEquals(List.of("committee@test.com"), emailsCaptor.getValue());
    }

    @Test
    void submitRental_noCommitteeMembers_skipsEmailNotification() {
        setupSubmitRentalBaseMocks("alice", 10L);
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of());

        service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null, "alice", null);

        verify(mailService, never()).sendRentalSubmittedToCommittee(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitRental_renterHasFullName_usesFullNameInCommitteeEmail() {
        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        renter.setFullName("Alice Wonderland");
        User committeeUser = userWith(50L, "committee");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(mainEquipmentStatusRepository.existsInteriorConflictingStatus(any(), any(), any())).thenReturn(false);
        when(rentalRepository.save(any())).thenAnswer(inv -> {
            EquipmentRental r = inv.getArgument(0);
            if (r.getId() == null) r.setId(100L);
            return r;
        });
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any()))
                .thenReturn(Optional.of(pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100))));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null, "alice", null);

        verify(mailService).sendRentalSubmittedToCommittee(any(), any(), eq("Alice Wonderland"), any(), any(), any(), any());
    }

    @Test
    void submitRental_renterHasNoFullName_usesUsernameInCommitteeEmail() {
        User committeeUser = userWith(50L, "committee");

        setupSubmitRentalBaseMocks("alice", 10L);
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null, "alice", null);

        // fullName is null in userWithRole → falls back to username
        verify(mailService).sendRentalSubmittedToCommittee(any(), any(), eq("alice"), any(), any(), any(), any());
    }

    @Test
    void submitRental_equipmentNullSerialNumber_usesEmDashInMainEqRows() {
        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        RentalCategory pricingCategory = RentalCategory.builder().id(1L).name(RentalPricingCategory.CAMERA).build();
        MainEquipment equipment = MainEquipment.builder()
                .mainEquipmentId(10L).brand("Sony").model("A7IV")
                .serialNumber(null).isForRent(true).pricingCategory(pricingCategory)
                .build();
        User committeeUser = userWith(50L, "committee");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(mainEquipmentStatusRepository.existsInteriorConflictingStatus(any(), any(), any())).thenReturn(false);
        when(rentalRepository.save(any())).thenAnswer(inv -> {
            EquipmentRental r = inv.getArgument(0);
            if (r.getId() == null) r.setId(100L);
            return r;
        });
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any()))
                .thenReturn(Optional.of(pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100))));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null, "alice", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String[]>> mainEqCaptor = ArgumentCaptor.forClass(List.class);
        verify(mailService).sendRentalSubmittedToCommittee(any(), any(), any(), mainEqCaptor.capture(), any(), any(), any());
        List<String[]> rows = mainEqCaptor.getValue();
        assertEquals(1, rows.size());
        assertEquals("Sony A7IV", rows.get(0)[0]);
        assertEquals("—", rows.get(0)[1]);
    }

    @Test
    void submitRental_equipmentWithSerialNumber_usesSerialNumberInMainEqRows() {
        User committeeUser = userWith(50L, "committee");

        setupSubmitRentalBaseMocks("alice", 10L);
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), null, "alice", null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String[]>> mainEqCaptor = ArgumentCaptor.forClass(List.class);
        verify(mailService).sendRentalSubmittedToCommittee(any(), any(), any(), mainEqCaptor.capture(), any(), any(), any());
        List<String[]> rows = mainEqCaptor.getValue();
        assertEquals(1, rows.size());
        assertEquals("Canon EOS R5", rows.get(0)[0]);
        assertEquals("SN-10", rows.get(0)[1]);
    }

    @Test
    void submitRental_withSubItems_populatesSubEqRowsInEmail() {
        User committeeUser = userWith(50L, "committee");
        SubEquipment sub = subEquipmentWith(20L, 5, RentalPricingCategory.SPEEDLIGHT);

        setupSubmitRentalBaseMocks("alice", 10L);
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.findBoundaryReturnQuantities(any(), any(), any(), any())).thenReturn(List.of());
        when(rentalSubItemRepository.findBoundaryPickupQuantities(any(), any(), any(), any())).thenReturn(List.of());
        when(pricingRepository.findByPricingCategory_NameAndMemberType(eq(RentalPricingCategory.SPEEDLIGHT), any()))
                .thenReturn(Optional.of(pricingWith(BigDecimal.valueOf(3), BigDecimal.valueOf(50))));
        when(rentalSubItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        service.submitRental(List.of(10L), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                null, "alice", List.of(new SubEquipmentEntry(20L, 2)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String[]>> subEqCaptor = ArgumentCaptor.forClass(List.class);
        verify(mailService).sendRentalSubmittedToCommittee(any(), any(), any(), any(), subEqCaptor.capture(), any(), any());
        List<String[]> rows = subEqCaptor.getValue();
        assertEquals(1, rows.size());
        assertEquals("Flash Godox", rows.get(0)[0]);
        assertEquals("x2", rows.get(0)[1]);
    }

    // ── reviewRental ──────────────────────────────────────────────────────────

    @Test
    void reviewRental_notPendingReview_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.APPROVED);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", null, null, null, null, "committee", null, null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void reviewRental_rejectAction_setsRejectedStatus() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PENDING_REVIEW);
        rental.setRenter(userWith(1L, "alice"));
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.reviewRental(RENTAL_ID, "REJECT", null, null,
                "Not available", "notes", "committee", null, null);

        assertEquals(RentalStatus.REJECTED, result.getStatus());
        assertEquals("Not available", result.getRejectionReason());
        verify(mailService).sendRentalRejectedToRenter(any(), any(), eq("Not available"));
    }

    @Test
    void reviewRental_invalidAction_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PENDING_REVIEW);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "HOLD", null, null, null, null, "committee", null, null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void reviewRental_approveAction_setsApprovedStatus() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        LocalDateTime pickup = start.minusDays(1).atTime(10, 0);
        LocalDateTime returnDt = end.plusDays(1).atTime(10, 0);

        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        EquipmentRental rental = EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(renter).programStartDate(start).programEndDate(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L).build();
        MainEquipment equipment = equipmentWith(10L, true, RentalPricingCategory.CAMERA);
        RentalPricing pricing = pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any())).thenReturn(Optional.of(pricing));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null,
                null, "OK", "committee", pickup, returnDt);

        assertEquals(RentalStatus.APPROVED, result.getStatus());
        assertEquals(pickup, result.getPickupDatetime());
        assertEquals(returnDt, result.getReturnDatetime());
        verify(receiptService).createInvoice(result);
        verify(mailService).sendRentalApprovedToRenter(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reviewRental_approveAction_pickupAfterProgramStart_throwsBadRequest() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        LocalDateTime pickup = start.plusDays(1).atTime(10, 0); // after start — invalid
        LocalDateTime returnDt = end.plusDays(1).atTime(10, 0);

        EquipmentRental rental = EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(userWith(1L, "alice")).programStartDate(start).programEndDate(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L).build();

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee", pickup,
                        returnDt));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void reviewRental_approveAction_returnDateBeforeProgramEnd_throwsBadRequest() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(5);
        LocalDateTime pickup = start.minusDays(1).atTime(10, 0);
        LocalDateTime returnDt = end.minusDays(2).atTime(10, 0); // before end — invalid

        EquipmentRental rental = EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(userWith(1L, "alice")).programStartDate(start).programEndDate(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L).build();

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee", pickup,
                        returnDt));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void reviewRental_approveAction_rentalConflict_throwsConflict() {
        LocalDate start = LocalDate.now().plusDays(1);
        LocalDate end = LocalDate.now().plusDays(3);
        LocalDateTime pickup = start.minusDays(1).atTime(10, 0);
        LocalDateTime returnDt = end.plusDays(1).atTime(10, 0);

        EquipmentRental rental = EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(userWithRole(1L, "alice", "ROLE_STUDENT")).programStartDate(start).programEndDate(end)
                .items(new ArrayList<>()).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L).build();

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee", pickup,
                        returnDt));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── initiatePayment ───────────────────────────────────────────────────────

    @Test
    void initiatePayment_validCashMethod_invokesHandlerAndSaves() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        PaymentMethodHandler handler = mock(PaymentMethodHandler.class);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(service, "handlerMap", Map.of(RentalPaymentMethod.CASH, handler));

        service.initiatePayment(RENTAL_ID, "CASH", "alice");

        verify(handler).initiate(rental, renter);
        verify(rentalRepository).save(rental);
    }

    @Test
    void initiatePayment_wrongUser_throwsForbidden() {
        User renter = userWith(1L, "alice");
        User other = userWith(2L, "bob");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(other));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.initiatePayment(RENTAL_ID, "CASH", "bob"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void initiatePayment_invalidPaymentMethodString_throwsBadRequest() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.initiatePayment(RENTAL_ID, "BITCOIN", "alice"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void initiatePayment_noHandlerRegistered_throwsBadRequest() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        ReflectionTestUtils.setField(service, "handlerMap", Map.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.initiatePayment(RENTAL_ID, "CASH", "alice"));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ── updateLogistics ───────────────────────────────────────────────────────

    @Test
    void updateLogistics_approvedRental_updatesPickupAndReturn() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setReviewedBy(null);
        LocalDateTime newPickup = rental.getProgramStartDate().minusDays(1).atTime(9, 0);
        LocalDateTime newReturn = rental.getProgramEndDate().plusDays(1).atTime(18, 0);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.updateLogistics(RENTAL_ID, "committee", newPickup, newReturn);

        assertEquals(newPickup, result.getPickupDatetime());
        assertEquals(newReturn, result.getReturnDatetime());
        verify(mailService).sendLogisticsUpdatedToRenter(any(), any(), eq(newPickup), eq(newReturn));
    }

    @Test
    void updateLogistics_pickedUpRental_updatesPickupAndReturn() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setStatus(RentalStatus.PICKED_UP);
        rental.setReviewedBy(null);
        LocalDateTime newPickup = rental.getProgramStartDate().minusDays(1).atTime(9, 0);
        LocalDateTime newReturn = rental.getProgramEndDate().plusDays(1).atTime(18, 0);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.updateLogistics(RENTAL_ID, "committee", newPickup, newReturn);

        assertEquals(RentalStatus.PICKED_UP, result.getStatus());
        assertEquals(newPickup, result.getPickupDatetime());
    }

    @Test
    void updateLogistics_wrongStatus_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PENDING_REVIEW);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));

        LocalDateTime pickup = LocalDate.now().atTime(9, 0);
        LocalDateTime returnDt = LocalDate.now().plusDays(3).atTime(18, 0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(RENTAL_ID, "committee", pickup, returnDt));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateLogistics_nonApproverWithActiveApprover_throwsForbidden() {
        User originalApprover = userWithRole(20L, "original", "ROLE_EQUIPMENT_COMMITTEE");
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setReviewedBy(originalApprover);

        User otherCommittee = userWith(11L, "other");
        LocalDateTime newPickup = rental.getProgramStartDate().minusDays(1).atTime(9, 0);
        LocalDateTime newReturn = rental.getProgramEndDate().plusDays(1).atTime(18, 0);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(otherCommittee));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(RENTAL_ID, "other", newPickup, newReturn));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void updateLogistics_pickupAfterProgramStart_throwsBadRequest() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setReviewedBy(null);
        // Pickup is after program start — invalid
        LocalDateTime badPickup = rental.getProgramStartDate().plusDays(1).atTime(9, 0);
        LocalDateTime returnDt = rental.getProgramEndDate().plusDays(1).atTime(18, 0);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(RENTAL_ID, "committee", badPickup, returnDt));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateLogistics_rentalConflict_throwsConflict() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setReviewedBy(null);

        // Add one item so the conflict check runs against something
        MainEquipment equipment = equipmentWith(10L, true, RentalPricingCategory.CAMERA);
        EquipmentRentalItem item = EquipmentRentalItem.builder()
                .mainEquipment(equipment).baseAmount(1000L).latePenaltyPerDay(50L)
                .latePenaltyAmount(0L).itemTotalAmount(1000L).build();
        rental.getItems().add(item);

        LocalDateTime newPickup = rental.getProgramStartDate().minusDays(1).atTime(9, 0);
        LocalDateTime newReturn = rental.getProgramEndDate().plusDays(1).atTime(18, 0);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateLogistics(RENTAL_ID, "committee", newPickup, newReturn));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── updateEquipment ───────────────────────────────────────────────────────

    @Test
    void updateEquipment_approvedRental_swapsEquipmentAndRegeneratesInvoice() {
        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(renter);
        rental.setReviewedBy(null);

        MainEquipment newEquipment = equipmentWith(20L, true, RentalPricingCategory.LENS_NORMAL);
        RentalPricing pricing = pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(80));

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(20L)).thenReturn(Optional.of(newEquipment));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any())).thenReturn(Optional.of(pricing));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(80));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.updateEquipment(RENTAL_ID, "committee", List.of(20L), null);

        assertEquals(RentalStatus.APPROVED, result.getStatus());
        verify(receiptService).deleteInvoice(RENTAL_ID);
        verify(receiptService).createInvoice(result);
        verify(mailService).sendEquipmentUpdatedToRenter(any(), any(), any());
    }

    @Test
    void updateEquipment_notApproved_throwsBadRequest() {
        EquipmentRental rental = rentalWith(RENTAL_ID, RentalStatus.PICKED_UP);
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "committee", List.of(10L), null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateEquipment_nonApproverWithActiveApprover_throwsForbidden() {
        User originalApprover = userWithRole(20L, "original", "ROLE_EQUIPMENT_COMMITTEE");
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setReviewedBy(originalApprover);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("other")).thenReturn(Optional.of(userWith(11L, "other")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "other", List.of(10L), null));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void updateEquipment_equipmentNotFound_throwsNotFound() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "committee", List.of(99L), null));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void updateEquipment_equipmentNotForRent_throwsBadRequest() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipmentWith(10L, false, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "committee", List.of(10L), null));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateEquipment_rentalConflict_throwsConflict() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "committee", List.of(10L), null));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void updateEquipment_statusConflict_throwsConflict() {
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "committee", List.of(10L), null));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private EquipmentRental rentalWith(Long id, RentalStatus status) {
        return EquipmentRental.builder()
                .id(id)
                .status(status)
                .programStartDate(LocalDate.now())
                .programEndDate(LocalDate.now().plusDays(3))
                .items(new ArrayList<>())
                .subItems(new ArrayList<>())
                .totalBaseAmount(0L)
                .totalPenaltyAmount(0L)
                .build();
    }

    private EquipmentRental rentalWithRenter(Long id, RentalStatus status, User renter) {
        EquipmentRental r = rentalWith(id, status);
        r.setRenter(renter);
        return r;
    }

    private User userWith(Long id, String username) {
        return User.builder().id(id).username(username).email(username + "@test.com").build();
    }

    private User userWithRole(Long id, String username, String roleName) {
        Role role = Role.builder().id(1L).name(roleName).build();
        return User.builder()
                .id(id).username(username).email(username + "@test.com")
                .roles(new HashSet<>(Set.of(role)))
                .build();
    }

    private MainEquipment equipmentWith(Long id, boolean forRent, RentalPricingCategory category) {
        RentalCategory pricingCategory = category != null
                ? RentalCategory.builder().id(1L).name(category).build()
                : null;
        return MainEquipment.builder()
                .mainEquipmentId(id).brand("Canon").model("EOS R5")
                .serialNumber("SN-" + id).isForRent(forRent)
                .pricingCategory(pricingCategory)
                .build();
    }

    private RentalPricing pricingWith(BigDecimal penaltyPerDay, BigDecimal rate) {
        return RentalPricing.builder()
                .id(1L).latePenaltyPerDay(penaltyPerDay)
                .rate1Day(rate).rate3Days(rate.multiply(BigDecimal.valueOf(2)))
                .ratePerDayExtra(rate).build();
    }

    // ── buildAndSaveSubItems (exercised via public callers) ───────────────────
    //
    // checkQuantity=false (boundary-aware) is reached via submitRental.
    // checkQuantity=true (strict) is reached via updateEquipment.

    @Test
    void buildAndSaveSubItems_subEquipmentNotFound_throwsNotFound() {
        setupSubmitRentalBaseMocks("alice", 10L);
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L),
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                        null, "alice", List.of(new SubEquipmentEntry(20L, 1))));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void buildAndSaveSubItems_quantityZero_throwsBadRequest() {
        setupSubmitRentalBaseMocks("alice", 10L);
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(subEquipmentWith(20L, 5, null)));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L),
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                        null, "alice", List.of(new SubEquipmentEntry(20L, 0))));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void buildAndSaveSubItems_submitPath_quantityFits_subItemAddedWithCorrectTotal() {
        // checkQuantity=false: minConcurrent=0, held=0, qty=2, totalQty=5 → ok
        setupSubmitRentalBaseMocks("alice", 10L);
        SubEquipment sub = subEquipmentWith(20L, 5, RentalPricingCategory.SPEEDLIGHT);

        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.findBoundaryReturnQuantities(any(), any(), any(), any())).thenReturn(List.of());
        when(rentalSubItemRepository.findBoundaryPickupQuantities(any(), any(), any(), any())).thenReturn(List.of());
        when(pricingRepository.findByPricingCategory_NameAndMemberType(eq(RentalPricingCategory.SPEEDLIGHT), any()))
                .thenReturn(Optional.of(pricingWith(BigDecimal.valueOf(3), BigDecimal.valueOf(50))));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of());

        EquipmentRental result = service.submitRental(List.of(10L),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                null, "alice", List.of(new SubEquipmentEntry(20L, 2)));

        assertEquals(1, result.getSubItems().size());
        assertEquals(2, result.getSubItems().get(0).getBorrowedQuantity());
        // main: toC(100) = 10000; sub: toC(100)*2 = 20000; total = 30000
        assertEquals(30000L, result.getTotalBaseAmount());
    }

    @Test
    void buildAndSaveSubItems_submitPath_quantityExceeded_throwsConflict() {
        // checkQuantity=false: totalCommitted=3, boundary=0 → minConcurrent=3, held=0,
        // qty=1, totalQty=3 → 4>3 → 409
        setupSubmitRentalBaseMocks("alice", 10L);
        SubEquipment sub = subEquipmentWith(20L, 3, null);

        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(3);
        when(rentalSubItemRepository.findBoundaryReturnQuantities(any(), any(), any(), any())).thenReturn(List.of());
        when(rentalSubItemRepository.findBoundaryPickupQuantities(any(), any(), any(), any())).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.submitRental(List.of(10L),
                        LocalDate.now().plusDays(1), LocalDate.now().plusDays(3),
                        null, "alice", List.of(new SubEquipmentEntry(20L, 1))));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void buildAndSaveSubItems_strictPath_quantityFits_subItemAddedWithCorrectQuantity() {
        // checkQuantity=true: committed=1, held=0, qty=2, totalQty=5 → 3≤5 → ok
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);
        SubEquipment sub = subEquipmentWith(20L, 5, RentalPricingCategory.SPEEDLIGHT);

        setupUpdateEquipmentBaseMocks(RENTAL_ID, rental);
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(1);
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.updateEquipment(RENTAL_ID, "committee", List.of(10L),
                List.of(new SubEquipmentEntry(20L, 2)));

        assertEquals(1, result.getSubItems().size());
        assertEquals(2, result.getSubItems().get(0).getBorrowedQuantity());
        // main: toC(100)=10000; sub: toC(100)*2=20000; total=30000
        assertEquals(30000L, result.getTotalBaseAmount());
    }

    @Test
    void buildAndSaveSubItems_strictPath_quantityExceeded_throwsConflict() {
        // checkQuantity=true: committed=3, held=0, qty=1, totalQty=3 → 4>3 → 409
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);
        SubEquipment sub = subEquipmentWith(20L, 3, null);

        setupUpdateEquipmentBaseMocks(RENTAL_ID, rental);
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(3);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.updateEquipment(RENTAL_ID, "committee", List.of(10L),
                        List.of(new SubEquipmentEntry(20L, 1))));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void buildAndSaveSubItems_noPricingCategory_subItemSavedWithZeroBaseAmount() {
        // sub-equipment with no pricingCategory → pricing lookup skipped → baseAmount=0
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);
        SubEquipment sub = subEquipmentWith(20L, 5, null); // no pricing category

        setupUpdateEquipmentBaseMocks(RENTAL_ID, rental);
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.updateEquipment(RENTAL_ID, "committee", List.of(10L),
                List.of(new SubEquipmentEntry(20L, 1)));

        assertEquals(1, result.getSubItems().size());
        assertEquals(0L, result.getSubItems().get(0).getBaseAmount());
        // totalBase = main(10000) + sub(0) = 10000
        assertEquals(10000L, result.getTotalBaseAmount());
    }

    @Test
    void buildAndSaveSubItems_noPricingRowFound_subItemSavedWithZeroBaseAmount() {
        // pricingCategory set but no matching row in DB → orElse(null) → baseAmount=0
        EquipmentRental rental = approvedRentalWithLogistics(RENTAL_ID);
        rental.setRenter(userWithRole(1L, "alice", "ROLE_STUDENT"));
        rental.setReviewedBy(null);
        SubEquipment sub = subEquipmentWith(20L, 5, RentalPricingCategory.SPEEDLIGHT);

        setupUpdateEquipmentBaseMocks(RENTAL_ID, rental);
        // Override SPEEDLIGHT → not found (any() in helper already covers CAMERA for
        // buildItems)
        when(pricingRepository.findByPricingCategory_NameAndMemberType(eq(RentalPricingCategory.SPEEDLIGHT), any()))
                .thenReturn(Optional.empty());
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.updateEquipment(RENTAL_ID, "committee", List.of(10L),
                List.of(new SubEquipmentEntry(20L, 1)));

        assertEquals(1, result.getSubItems().size());
        assertEquals(0L, result.getSubItems().get(0).getBaseAmount());
        assertEquals(10000L, result.getTotalBaseAmount());
    }

    // ── checkSubEquipmentQuantities (exercised via reviewRental) ─────────────
    //
    // Triggered when reviewRental is called with null/empty subEquipmentEntries,
    // re-validating the quantities of existing sub-items on the rental.

    @Test
    void reviewRental_approveAction_noExistingSubItems_skipsSubItemQuantityCheck() {
        EquipmentRental rental = buildReviewRentalWithSubItems(List.of());
        setupReviewApproveBaseForSubItemTests(rental);
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee",
                rental.getProgramStartDate().minusDays(1).atTime(10, 0),
                rental.getProgramEndDate().plusDays(1).atTime(10, 0));

        verify(rentalSubItemRepository, never()).sumCommittedQuantity(any(), any(), any(), any());
        verify(subEquipmentQuantityHoldRepository, never()).sumHeldQuantity(any(), any(), any(), any());
    }

    @Test
    void reviewRental_approveAction_existingSubItemQuantityFits_succeeds() {
        // committed=1, held=0, borrowed=2, totalQty=5 → 3 ≤ 5 → ok
        SubEquipment sub = subEquipmentWith(20L, 5, null);
        EquipmentRentalSubItem subItem = subItemWith(sub, 2, 5000L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(subItem)));
        setupReviewApproveBaseForSubItemTests(rental);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(1);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.reviewRental(
                RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee",
                rental.getProgramStartDate().minusDays(1).atTime(10, 0),
                rental.getProgramEndDate().plusDays(1).atTime(10, 0)));
    }

    @Test
    void reviewRental_approveAction_existingSubItemExactlyAtCapacity_succeeds() {
        // committed=3, held=0, borrowed=2, totalQty=5 → 5 == 5 → ok (boundary)
        SubEquipment sub = subEquipmentWith(20L, 5, null);
        EquipmentRentalSubItem subItem = subItemWith(sub, 2, 5000L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(subItem)));
        setupReviewApproveBaseForSubItemTests(rental);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(3);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.reviewRental(
                RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee",
                rental.getProgramStartDate().minusDays(1).atTime(10, 0),
                rental.getProgramEndDate().plusDays(1).atTime(10, 0)));
    }

    @Test
    void reviewRental_approveAction_existingSubItemCommittedExceedsCapacity_throwsConflict() {
        // committed=4, held=0, borrowed=2, totalQty=5 → 6 > 5 → 409
        SubEquipment sub = subEquipmentWith(20L, 5, null);
        EquipmentRentalSubItem subItem = subItemWith(sub, 2, 5000L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(subItem)));
        setupReviewApproveBaseForSubItemTests(rental);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(4);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee",
                        rental.getProgramStartDate().minusDays(1).atTime(10, 0),
                        rental.getProgramEndDate().plusDays(1).atTime(10, 0)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void reviewRental_approveAction_existingSubItemHeldQuantityExceedsCapacity_throwsConflict() {
        // committed=0, held=4, borrowed=2, totalQty=5 → 6 > 5 → 409
        SubEquipment sub = subEquipmentWith(20L, 5, null);
        EquipmentRentalSubItem subItem = subItemWith(sub, 2, 5000L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(subItem)));
        setupReviewApproveBaseForSubItemTests(rental);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(0);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(4);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee",
                        rental.getProgramStartDate().minusDays(1).atTime(10, 0),
                        rental.getProgramEndDate().plusDays(1).atTime(10, 0)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void reviewRental_approveAction_multipleSubItems_secondExceedsCapacity_throwsConflict() {
        // first item fits, second overflows → still throws 409
        SubEquipment sub1 = subEquipmentWith(20L, 5, null);
        SubEquipment sub2 = subEquipmentWith(21L, 3, null);
        EquipmentRentalSubItem item1 = subItemWith(sub1, 2, 3000L);
        EquipmentRentalSubItem item2 = subItemWith(sub2, 2, 2000L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(item1, item2)));
        setupReviewApproveBaseForSubItemTests(rental);
        // First call (sub1): committed=1, held=0, borrowed=2, totalQty=5 → 3 ≤ 5 → ok
        // Second call (sub2): committed=2, held=0, borrowed=2, totalQty=3 → 4 > 3 → 409
        when(rentalSubItemRepository.sumCommittedQuantity(eq(20L), any(), any(), any())).thenReturn(1);
        when(rentalSubItemRepository.sumCommittedQuantity(eq(21L), any(), any(), any())).thenReturn(2);
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L), null, null, null, "committee",
                        rental.getProgramStartDate().minusDays(1).atTime(10, 0),
                        rental.getProgramEndDate().plusDays(1).atTime(10, 0)));
        assertEquals(409, ex.getStatusCode().value());
    }

    // ── private setup helpers ─────────────────────────────────────────────────

    private void setupSubmitRentalBaseMocks(String username, Long equipmentId) {
        when(userRepository.findByUsername(username))
                .thenReturn(Optional.of(userWithRole(1L, username, "ROLE_STUDENT")));
        when(mainEquipmentRepository.findById(equipmentId))
                .thenReturn(Optional.of(equipmentWith(equipmentId, true, RentalPricingCategory.CAMERA)));
        when(mainEquipmentStatusRepository.existsInteriorConflictingStatus(any(), any(), any())).thenReturn(false);
        when(rentalRepository.save(any())).thenAnswer(inv -> {
            EquipmentRental r = inv.getArgument(0);
            if (r.getId() == null)
                r.setId(100L);
            return r;
        });
        when(pricingRepository.findByPricingCategory_NameAndMemberType(eq(RentalPricingCategory.CAMERA), any()))
                .thenReturn(Optional.of(pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100))));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        // Note: findAllByRoleName is only called when submitRental succeeds — add
        // inline in success tests.
    }

    private void setupUpdateEquipmentBaseMocks(Long rentalId, EquipmentRental rental) {
        when(rentalRepository.findById(rentalId)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        // Covers any category — specific tests can override with eq(...) after calling
        // this
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any()))
                .thenReturn(Optional.of(pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100))));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        // Note: rentalRepository.save is only called when updateEquipment succeeds —
        // add inline in success tests.
    }

    private SubEquipment subEquipmentWith(Long id, int totalQty, RentalPricingCategory category) {
        RentalCategory pricingCategory = category != null
                ? RentalCategory.builder().id(2L).name(category).build()
                : null;
        return SubEquipment.builder()
                .subEquipmentId(id).type("Flash").brand("Godox")
                .totalQuantity(totalQty).pricingCategory(pricingCategory)
                .build();
    }

    private EquipmentRentalSubItem subItemWith(SubEquipment sub, int borrowedQty, long baseAmount) {
        return EquipmentRentalSubItem.builder()
                .subEquipment(sub).borrowedQuantity(borrowedQty)
                .baseAmount(baseAmount).latePenaltyPerDay(0L)
                .latePenaltyAmount(0L).itemTotalAmount(baseAmount)
                .build();
    }

    private EquipmentRental buildReviewRentalWithSubItems(List<EquipmentRentalSubItem> subItems) {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        return EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(userWithRole(1L, "alice", "ROLE_STUDENT"))
                .programStartDate(start).programEndDate(end)
                .items(new ArrayList<>()).subItems(subItems)
                .totalBaseAmount(0L).totalPenaltyAmount(0L)
                .build();
    }

    private void setupReviewApproveBaseForSubItemTests(EquipmentRental rental) {
        RentalPricing pricing = pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100));
        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L))
                .thenReturn(Optional.of(equipmentWith(10L, true, RentalPricingCategory.CAMERA)));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any())).thenReturn(Optional.of(pricing));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        // rentalRepository.save is only reached when the method succeeds — add it inline in success tests.
    }

    private EquipmentRental approvedRentalWithLogistics(Long id) {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        return EquipmentRental.builder()
                .id(id).status(RentalStatus.APPROVED)
                .renter(userWithRole(1L, "alice", "ROLE_STUDENT"))
                .programStartDate(start).programEndDate(end)
                .pickupDatetime(start.minusDays(1).atTime(10, 0))
                .returnDatetime(end.plusDays(1).atTime(10, 0))
                .durationDays(4)
                .items(new ArrayList<>()).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L)
                .build();
    }

    // ── getEquipmentSchedules ─────────────────────────────────────────────────

    @Test
    void getEquipmentSchedules_notOwnerNotCommittee_throwsForbidden() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        User stranger = userWith(99L, "stranger");

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("stranger")).thenReturn(Optional.of(stranger));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getEquipmentSchedules(RENTAL_ID, null, null, "stranger"));
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void getEquipmentSchedules_ownerWithNoCommitteeRole_succeeds() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(RENTAL_ID, List.of(), List.of(), "alice");

        assertNotNull(result);
        assertTrue(result.mainEquipment().isEmpty());
        assertTrue(result.subEquipment().isEmpty());
    }

    @Test
    void getEquipmentSchedules_committeeUserNonOwner_succeeds() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        User committee = userWithRole(10L, "committee", "ROLE_EQUIPMENT_COMMITTEE");

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(committee));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(RENTAL_ID, List.of(), List.of(), "committee");

        assertNotNull(result);
    }

    @Test
    void getEquipmentSchedules_explicitIdsUsed_doesNotFallbackToEntityItems() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        MainEquipment eq = equipmentWith(50L, true, null);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(List.of(50L))).thenReturn(List.of());
        when(mainEquipmentRepository.findById(50L)).thenReturn(Optional.of(eq));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(RENTAL_ID, List.of(50L), List.of(), "alice");

        assertEquals(1, result.mainEquipment().size());
        assertEquals(50L, result.mainEquipment().get(0).mainEquipmentId());
    }

    @Test
    void getEquipmentSchedules_nullIds_fallsBackToEntityItems() {
        User renter = userWith(1L, "alice");
        MainEquipment eq = equipmentWith(100L, true, null);
        EquipmentRentalItem item = EquipmentRentalItem.builder().mainEquipment(eq).build();
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        rental.setItems(new ArrayList<>(List.of(item)));

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(List.of(100L))).thenReturn(List.of());
        when(mainEquipmentRepository.findById(100L)).thenReturn(Optional.of(eq));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(RENTAL_ID, null, null, "alice");

        assertEquals(1, result.mainEquipment().size());
        assertEquals(100L, result.mainEquipment().get(0).mainEquipmentId());
    }

    @Test
    void getEquipmentSchedules_emptyMainIds_skipsStatusRepoCall() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(RENTAL_ID, List.of(), List.of(), "alice");

        verify(mainEquipmentStatusRepository, never()).findAllByEquipmentIds(any());
        assertTrue(result.mainEquipment().isEmpty());
    }

    @Test
    void getEquipmentSchedules_emptySubIds_skipsHoldRepoCall() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(RENTAL_ID, List.of(), List.of(), "alice");

        verify(subEquipmentQuantityHoldRepository, never()).findAllBySubEquipmentIds(any());
        assertTrue(result.subEquipment().isEmpty());
    }

    @Test
    void getEquipmentSchedules_mainEquipmentNotFound_throwsNotFound() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(any())).thenReturn(List.of());
        when(mainEquipmentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getEquipmentSchedules(RENTAL_ID, List.of(999L), List.of(), "alice"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getEquipmentSchedules_subEquipmentNotFound_throwsNotFound() {
        User renter = userWith(1L, "alice");
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(subEquipmentQuantityHoldRepository.findAllBySubEquipmentIds(any())).thenReturn(List.of());
        when(subEquipmentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getEquipmentSchedules(RENTAL_ID, List.of(), List.of(999L), "alice"));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getEquipmentSchedules_happyPath_returnsStatusesAndHoldsAndBorrowedQty() {
        User renter = userWith(1L, "alice");
        MainEquipment eq = equipmentWith(10L, true, null);
        SubEquipment sub = SubEquipment.builder().subEquipmentId(20L)
                .type("Flash").brand("Godox").totalQuantity(5).build();

        EquipmentRentalSubItem subItem = EquipmentRentalSubItem.builder()
                .subEquipment(sub).borrowedQuantity(3).build();
        EquipmentRental rental = rentalWithRenter(RENTAL_ID, RentalStatus.APPROVED, renter);
        rental.setSubItems(new ArrayList<>(List.of(subItem)));

        LocalDateTime now = LocalDateTime.now();
        MainEquipmentStatus status = MainEquipmentStatus.builder()
                .id(1L).mainEquipment(eq).statusType(MainEquipmentStatusType.IN_USE)
                .startDatetime(now).endDatetime(now.plusDays(1)).notes("test status").build();
        SubEquipmentQuantityHold hold = SubEquipmentQuantityHold.builder()
                .id(2L).subEquipment(sub).quantity(2)
                .startDatetime(now).endDatetime(now.plusDays(1)).notes("test hold").build();

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(renter));
        when(mainEquipmentStatusRepository.findAllByEquipmentIds(List.of(10L))).thenReturn(List.of(status));
        when(subEquipmentQuantityHoldRepository.findAllBySubEquipmentIds(List.of(20L))).thenReturn(List.of(hold));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(eq));
        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));

        EquipmentSchedulesResponse result = service.getEquipmentSchedules(
                RENTAL_ID, List.of(10L), List.of(20L), "alice");

        assertEquals(1, result.mainEquipment().size());
        MainEquipmentScheduleEntry mainEntry = result.mainEquipment().get(0);
        assertEquals(10L, mainEntry.mainEquipmentId());
        assertEquals(1, mainEntry.statuses().size());
        assertEquals(MainEquipmentStatusType.IN_USE, mainEntry.statuses().get(0).statusType());

        assertEquals(1, result.subEquipment().size());
        SubEquipmentScheduleEntry subEntry = result.subEquipment().get(0);
        assertEquals(20L, subEntry.subEquipmentId());
        assertEquals(3, subEntry.borrowedQuantity());
        assertEquals(1, subEntry.holds().size());
        assertEquals(2, subEntry.holds().get(0).quantity());
    }

    // ── getAllRentals (paginated, with equipmentId overload) ──────────────────

    @Test
    void getAllRentals_withEquipmentId_callsSearchRentalsByEquipment() {
        Page<EquipmentRental> expected = new PageImpl<>(List.of());
        when(rentalRepository.searchRentalsByEquipment(eq(5L), eq("canon"), eq("APPROVED"), any()))
                .thenReturn(expected);

        Page<EquipmentRental> result = service.getAllRentals(5L, "canon", "APPROVED", 0, 10);

        assertSame(expected, result);
        verify(rentalRepository).searchRentalsByEquipment(eq(5L), eq("canon"), eq("APPROVED"), any());
        verify(rentalRepository, never()).searchRentals(any(), any(), any());
    }

    @Test
    void getAllRentals_withoutEquipmentId_callsSearchRentals() {
        Page<EquipmentRental> expected = new PageImpl<>(List.of());
        when(rentalRepository.searchRentals(eq("canon"), eq("APPROVED"), any())).thenReturn(expected);

        Page<EquipmentRental> result = service.getAllRentals(null, "canon", "APPROVED", 0, 10);

        assertSame(expected, result);
        verify(rentalRepository).searchRentals(eq("canon"), eq("APPROVED"), any());
        verify(rentalRepository, never()).searchRentalsByEquipment(any(), any(), any(), any());
    }

    @Test
    void getAllRentals_negativePage_clampedToZero() {
        when(rentalRepository.searchRentals(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.getAllRentals(null, null, null, -5, 10);

        verify(rentalRepository).searchRentals(any(), any(), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
    }

    @Test
    void getAllRentals_sizeExceedsMax_clampedTo100() {
        when(rentalRepository.searchRentals(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.getAllRentals(null, null, null, 0, 200);

        verify(rentalRepository).searchRentals(any(), any(), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getAllRentals_nullSearchAndStatus_normalizedToEmpty() {
        when(rentalRepository.searchRentals(eq(""), eq(""), any())).thenReturn(new PageImpl<>(List.of()));

        service.getAllRentals(null, null, null, 0, 10);

        verify(rentalRepository).searchRentals(eq(""), eq(""), any());
    }

    // ── reviewRental – null/empty equipmentIds fallback to entity items ───────

    @Test
    void reviewRental_approveAction_nullEquipmentIds_fallsBackToEntityItems() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        LocalDateTime pickup = start.minusDays(1).atTime(10, 0);
        LocalDateTime returnDt = end.plusDays(1).atTime(10, 0);

        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        MainEquipment equipment = equipmentWith(10L, true, RentalPricingCategory.CAMERA);
        EquipmentRentalItem item = EquipmentRentalItem.builder().mainEquipment(equipment).build();
        EquipmentRental rental = EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(renter).programStartDate(start).programEndDate(end)
                .items(new ArrayList<>(List.of(item))).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L).build();
        RentalPricing pricing = pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any())).thenReturn(Optional.of(pricing));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // null equipmentIds → falls back to item[0].mainEquipment.id = 10L
        EquipmentRental result = service.reviewRental(RENTAL_ID, "APPROVE", null, null,
                null, null, "committee", pickup, returnDt);

        assertEquals(RentalStatus.APPROVED, result.getStatus());
        verify(mainEquipmentRepository).findById(10L);
    }

    @Test
    void reviewRental_approveAction_emptyEquipmentIds_fallsBackToEntityItems() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);
        LocalDateTime pickup = start.minusDays(1).atTime(10, 0);
        LocalDateTime returnDt = end.plusDays(1).atTime(10, 0);

        User renter = userWithRole(1L, "alice", "ROLE_STUDENT");
        MainEquipment equipment = equipmentWith(10L, true, RentalPricingCategory.CAMERA);
        EquipmentRentalItem item = EquipmentRentalItem.builder().mainEquipment(equipment).build();
        EquipmentRental rental = EquipmentRental.builder()
                .id(RENTAL_ID).status(RentalStatus.PENDING_REVIEW)
                .renter(renter).programStartDate(start).programEndDate(end)
                .items(new ArrayList<>(List.of(item))).subItems(new ArrayList<>())
                .totalBaseAmount(0L).totalPenaltyAmount(0L).build();
        RentalPricing pricing = pricingWith(BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        when(rentalRepository.findById(RENTAL_ID)).thenReturn(Optional.of(rental));
        when(userRepository.findByUsername("committee")).thenReturn(Optional.of(userWith(10L, "committee")));
        when(mainEquipmentRepository.findById(10L)).thenReturn(Optional.of(equipment));
        when(rentalItemRepository.existsConflictingApprovedRental(any(), any(), any(), any(), any())).thenReturn(false);
        when(requestEventItemRepository.existsConflictingRequest(any(), any(), any(), any(), any())).thenReturn(false);
        when(mainEquipmentStatusRepository.existsConflictingStatus(any(), any(), any())).thenReturn(false);
        when(pricingRepository.findByPricingCategory_NameAndMemberType(any(), any())).thenReturn(Optional.of(pricing));
        when(rentalPricingService.calculateCost(any(), anyInt())).thenReturn(BigDecimal.valueOf(100));
        when(rentalItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // empty list equipmentIds → falls back to item[0].mainEquipment.id = 10L
        EquipmentRental result = service.reviewRental(RENTAL_ID, "APPROVE", List.of(), null,
                null, null, "committee", pickup, returnDt);

        assertEquals(RentalStatus.APPROVED, result.getStatus());
        verify(mainEquipmentRepository).findById(10L);
    }

    // ── reviewRental – subEquipmentEntries branch: clears and rebuilds ────────

    @Test
    void reviewRental_approveAction_withSubEquipmentEntries_clearsAndRebuildSubItems() {
        SubEquipment sub = subEquipmentWith(20L, 5, RentalPricingCategory.SPEEDLIGHT);
        EquipmentRentalSubItem oldSubItem = subItemWith(sub, 3, 3000L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(oldSubItem)));
        setupReviewApproveBaseForSubItemTests(rental);

        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(1);
        when(rentalSubItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(rentalRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EquipmentRental result = service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L),
                List.of(new SubEquipmentEntry(20L, 2)),
                null, null, "committee", null, null);

        assertEquals(RentalStatus.APPROVED, result.getStatus());
        assertEquals(1, result.getSubItems().size());
        assertEquals(2, result.getSubItems().get(0).getBorrowedQuantity());
        assertEquals(20L, result.getSubItems().get(0).getSubEquipment().getSubEquipmentId());
    }

    @Test
    void reviewRental_approveAction_withSubEquipmentEntries_quantityExceeded_throwsConflict() {
        SubEquipment sub = subEquipmentWith(20L, 3, null);
        EquipmentRentalSubItem oldSubItem = subItemWith(sub, 1, 0L);
        EquipmentRental rental = buildReviewRentalWithSubItems(new ArrayList<>(List.of(oldSubItem)));
        setupReviewApproveBaseForSubItemTests(rental);

        when(subEquipmentRepository.findById(20L)).thenReturn(Optional.of(sub));
        when(subEquipmentQuantityHoldRepository.sumHeldQuantity(any(), any(), any(), any())).thenReturn(0);
        when(rentalSubItemRepository.sumCommittedQuantity(any(), any(), any(), any())).thenReturn(3);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.reviewRental(RENTAL_ID, "APPROVE", List.of(10L),
                        List.of(new SubEquipmentEntry(20L, 1)),
                        null, null, "committee", null, null));

        assertEquals(409, ex.getStatusCode().value());
    }
}
