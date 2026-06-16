package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.*;
import com.ifoto.ifoto_backend.repository.PaymentRepository;
import com.ifoto.ifoto_backend.service.BillplzXSignatureService;
import com.ifoto.ifoto_backend.service.MailService;
import com.ifoto.ifoto_backend.service.PaymentService;
import com.ifoto.ifoto_backend.service.ReceiptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BillplzXSignatureService xSignatureService;
    @Mock private ReceiptService receiptService;
    @Mock private MailService mailService;
    @Mock private BillplzConfig billplzConfig;

    @InjectMocks private PaymentService service;

    private User renter;
    private User approver;
    private Receipt stubReceipt;

    @BeforeEach
    void setUp() {
        renter = User.builder().id(1L).email("renter@test.com").username("renter").build();
        approver = User.builder().id(2L).email("approver@test.com").username("approver").build();
        stubReceipt = new Receipt();
        stubReceipt.setReceiptNumber("RC1110001");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, String> callbackParams(String paid, String state, String billId) {
        Map<String, String> p = new HashMap<>();
        p.put("id", billId);
        p.put("paid", paid);
        p.put("state", state);
        p.put("x_signature", "sig");
        p.put("transaction_id", "txn1");
        p.put("payment_method", "online_banking");
        return p;
    }

    private EquipmentRental rentalWith(RentalStatus status, Long penalty, LocalDate programStart) {
        return EquipmentRental.builder()
                .id(10L)
                .rentalNumber("ER-2026-000001")
                .renter(renter)
                .reviewedBy(approver)
                .status(status)
                .totalPenaltyAmount(penalty)
                .programStartDate(programStart)
                .paymentMethod(RentalPaymentMethod.ONLINE)
                .paymentStatus(RentalPaymentStatus.NONE)
                .build();
    }

    private Payment paymentFor(EquipmentRental rental, String billId) {
        Payment p = new Payment();
        p.setEquipmentRental(rental);
        p.setBillId(billId);
        p.setAmount(5000L);
        return p;
    }

    // ── handleBillplzCallback ─────────────────────────────────────────────────

    @Test
    void handleBillplzCallback_invalidSignature_returnsEarlyWithNoPaymentLookup() {
        when(xSignatureService.verify(any(), any())).thenReturn(false);

        service.handleBillplzCallback(callbackParams("true", "paid", "B1"));

        verify(paymentRepository, never()).findByBillId(any());
    }

    @Test
    void handleBillplzCallback_paymentNotFound_returnsEarlyWithNoSave() {
        when(xSignatureService.verify(any(), any())).thenReturn(true);
        when(paymentRepository.findByBillId("B1")).thenReturn(Optional.empty());

        service.handleBillplzCallback(callbackParams("true", "paid", "B1"));

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleBillplzCallback_paid_regularRental_programStartPast_setsActive() {
        when(xSignatureService.verify(any(), any())).thenReturn(true);
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().minusDays(1));
        Payment payment = paymentFor(rental, "B1");
        when(paymentRepository.findByBillId("B1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.handleBillplzCallback(callbackParams("true", "paid", "B1"));

        assertEquals(RentalStatus.ACTIVE, rental.getStatus());
        assertEquals(RentalPaymentStatus.ONLINE_PAID, rental.getPaymentStatus());
    }

    @Test
    void handleBillplzCallback_paid_regularRental_programStartFuture_setsPaid() {
        when(xSignatureService.verify(any(), any())).thenReturn(true);
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(5));
        Payment payment = paymentFor(rental, "B1");
        when(paymentRepository.findByBillId("B1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.handleBillplzCallback(callbackParams("true", "paid", "B1"));

        assertEquals(RentalStatus.PAID, rental.getStatus());
        assertEquals(RentalPaymentStatus.ONLINE_PAID, rental.getPaymentStatus());
    }

    @Test
    void handleBillplzCallback_paid_penaltyRental_setsReturnedAndPenaltyPaid() {
        when(xSignatureService.verify(any(), any())).thenReturn(true);
        EquipmentRental rental = rentalWith(RentalStatus.RETURNED, 500L, LocalDate.now().minusDays(10));
        Payment payment = paymentFor(rental, "B1");
        when(paymentRepository.findByBillId("B1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createOverdueReceipt(any(), any())).thenReturn(stubReceipt);

        service.handleBillplzCallback(callbackParams("true", "paid", "B1"));

        assertEquals(RentalStatus.RETURNED, rental.getStatus());
        assertEquals(RentalPaymentStatus.PENALTY_PAID, rental.getPaymentStatus());
        verify(mailService).sendOverduePaymentConfirmedToRenter(
                eq("renter@test.com"), eq("ER-2026-000001"), eq("RC1110001"));
        verify(mailService).sendOverduePaymentConfirmedToCommittee(
                eq("approver@test.com"), eq("ER-2026-000001"), anyString(), eq("RC1110001"));
    }

    @Test
    void handleBillplzCallback_paid_sendsConfirmationEmail() {
        when(xSignatureService.verify(any(), any())).thenReturn(true);
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(3));
        Payment payment = paymentFor(rental, "B1");
        when(paymentRepository.findByBillId("B1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.handleBillplzCallback(callbackParams("true", "paid", "B1"));

        verify(mailService).sendPaymentConfirmedToRenter(
                eq("renter@test.com"), eq("ER-2026-000001"), eq("RC1110001"));
        verify(mailService).sendPaymentConfirmedToCommittee(
                eq("approver@test.com"), eq("ER-2026-000001"), anyString(), eq("RC1110001"));
    }

    @Test
    void handleBillplzCallback_paidFalse_setsPaymentFailed() {
        when(xSignatureService.verify(any(), any())).thenReturn(true);
        EquipmentRental rental = rentalWith(RentalStatus.PENDING_PAYMENT, 0L, LocalDate.now().plusDays(3));
        Payment payment = paymentFor(rental, "B1");
        when(paymentRepository.findByBillId("B1")).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.handleBillplzCallback(callbackParams("false", "failed", "B1"));

        assertEquals(PaymentRecordStatus.FAILED, payment.getStatus());
    }

    // ── confirmCashPayment ────────────────────────────────────────────────────

    @Test
    void confirmCashPayment_paymentNotFound_throwsNotFound() {
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(1));
        User committee = User.builder().id(2L).build();
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.confirmCashPayment(rental, committee));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void confirmCashPayment_regularCash_programStartPast_setsCashPaidAndActive() {
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().minusDays(1));
        rental.setPaymentMethod(RentalPaymentMethod.CASH);
        User committee = User.builder().id(2L).build();
        Payment payment = paymentFor(rental, null);
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.confirmCashPayment(rental, committee);

        assertEquals(RentalPaymentStatus.CASH_PAID, rental.getPaymentStatus());
        assertEquals(RentalStatus.ACTIVE, rental.getStatus());
    }

    @Test
    void confirmCashPayment_regularCash_programStartFuture_setsCashPaidAndPaid() {
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(5));
        rental.setPaymentMethod(RentalPaymentMethod.CASH);
        User committee = User.builder().id(2L).build();
        Payment payment = paymentFor(rental, null);
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.confirmCashPayment(rental, committee);

        assertEquals(RentalPaymentStatus.CASH_PAID, rental.getPaymentStatus());
        assertEquals(RentalStatus.PAID, rental.getStatus());
    }

    @Test
    void confirmCashPayment_regularBankTransfer_setsBankTransferPaid() {
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(5));
        rental.setPaymentMethod(RentalPaymentMethod.BANK_TRANSFER);
        User committee = User.builder().id(2L).build();
        Payment payment = paymentFor(rental, null);
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.confirmCashPayment(rental, committee);

        assertEquals(RentalPaymentStatus.BANK_TRANSFER_PAID, rental.getPaymentStatus());
    }

    @Test
    void confirmCashPayment_penaltyPayment_setsReturnedAndPenaltyPaid() {
        EquipmentRental rental = rentalWith(RentalStatus.RETURNED, 300L, LocalDate.now().minusDays(10));
        rental.setPaymentMethod(RentalPaymentMethod.CASH);
        User committee = User.builder().id(2L).build();
        Payment payment = paymentFor(rental, null);
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createOverdueReceipt(any(), any())).thenReturn(stubReceipt);

        service.confirmCashPayment(rental, committee);

        assertEquals(RentalStatus.RETURNED, rental.getStatus());
        assertEquals(RentalPaymentStatus.PENALTY_PAID, rental.getPaymentStatus());
        verify(mailService).sendOverduePaymentConfirmedToRenter(
                eq("renter@test.com"), anyString(), eq("RC1110001"));
        verify(mailService).sendOverduePaymentConfirmedToCommittee(
                eq("approver@test.com"), anyString(), anyString(), eq("RC1110001"));
    }

    @Test
    void confirmCashPayment_setsPaymentConfirmedByCommittee() {
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(1));
        rental.setPaymentMethod(RentalPaymentMethod.CASH);
        User committee = User.builder().id(2L).username("committee").build();
        Payment payment = paymentFor(rental, null);
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.confirmCashPayment(rental, committee);

        assertEquals(committee, payment.getConfirmedBy());
        assertNotNull(payment.getConfirmedAt());
        assertEquals(PaymentRecordStatus.PAID, payment.getStatus());
        assertEquals(payment.getAmount(), payment.getPaidAmount());
    }

    @Test
    void confirmCashPayment_sendsConfirmationEmail() {
        EquipmentRental rental = rentalWith(RentalStatus.PICKED_UP, 0L, LocalDate.now().plusDays(1));
        rental.setPaymentMethod(RentalPaymentMethod.CASH);
        User committee = User.builder().id(2L).build();
        Payment payment = paymentFor(rental, null);
        when(paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(receiptService.createReceipt(any(), any())).thenReturn(stubReceipt);

        service.confirmCashPayment(rental, committee);

        verify(mailService).sendPaymentConfirmedToRenter(
                eq("renter@test.com"), anyString(), eq("RC1110001"));
        verify(mailService).sendPaymentConfirmedToCommittee(
                eq("approver@test.com"), anyString(), anyString(), eq("RC1110001"));
    }
}
