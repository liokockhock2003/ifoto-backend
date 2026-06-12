package com.ifoto.ifoto_backend.unit.service.payment;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.PaymentRepository;
import com.ifoto.ifoto_backend.service.BillplzService;
import com.ifoto.ifoto_backend.service.payment.OnlinePaymentHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnlinePaymentHandlerTest {

    @Mock private BillplzService billplzService;
    @Mock private PaymentRepository paymentRepository;
    @Mock private BillplzConfig billplzConfig;

    @InjectMocks
    private OnlinePaymentHandler handler;

    @Test
    void initiate_penaltyAlreadyPaid_throwsBadRequest() {
        EquipmentRental rental = rental(RentalStatus.RETURNED, RentalPaymentStatus.PENALTY_PAID, 1000L, 200L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> handler.initiate(rental, renter()));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void initiate_alreadyPendingPayment_returnsEarly() {
        EquipmentRental rental = rental(RentalStatus.PENDING_PAYMENT, RentalPaymentStatus.ONLINE_PENDING, 5000L, 0L);

        // Should return immediately without calling billplz or saving a payment
        handler.initiate(rental, renter());

        verify(billplzService, never()).createBill(any(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiate_notPickedUpAndNotPenalty_throwsBadRequest() {
        EquipmentRental rental = rental(RentalStatus.APPROVED, RentalPaymentStatus.NONE, 5000L, 0L);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> handler.initiate(rental, renter()));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void initiate_pickedUpStatus_createsBillplzBillAndUpdatesRental() {
        EquipmentRental rental = rental(RentalStatus.PICKED_UP, RentalPaymentStatus.NONE, 5000L, 0L);
        rental.setRentalNumber("R-0001");

        com.ifoto.ifoto_backend.model.Payment savedPayment = com.ifoto.ifoto_backend.model.Payment.builder()
                .id(1L).build();
        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(billplzConfig.getCollectionId()).thenReturn("coll-123");

        BillplzService.BillplzBillResponse billResponse =
                new BillplzService.BillplzBillResponse("bill-123", "https://pay.billplz.com/bill-123", null);
        when(billplzService.createBill(any(), any(), anyLong())).thenReturn(billResponse);

        handler.initiate(rental, renter());

        assertEquals(RentalStatus.PENDING_PAYMENT, rental.getStatus());
        assertEquals(RentalPaymentStatus.ONLINE_PENDING, rental.getPaymentStatus());
        verify(billplzService).createBill(any(), any(), eq(5000L));
    }

    @Test
    void initiate_penaltyPayment_usesPenaltyAmountForBill() {
        EquipmentRental rental = rental(RentalStatus.RETURNED, RentalPaymentStatus.NONE, 5000L, 800L);
        rental.setRentalNumber("R-0002");

        com.ifoto.ifoto_backend.model.Payment savedPayment = com.ifoto.ifoto_backend.model.Payment.builder()
                .id(1L).build();
        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(billplzConfig.getCollectionId()).thenReturn("coll-123");

        BillplzService.BillplzBillResponse billResponse =
                new BillplzService.BillplzBillResponse("bill-456", "https://pay.billplz.com/bill-456", null);
        when(billplzService.createBill(any(), any(), anyLong())).thenReturn(billResponse);

        handler.initiate(rental, renter());

        verify(billplzService).createBill(any(), any(), eq(800L)); // penalty amount
    }

    private EquipmentRental rental(RentalStatus status, RentalPaymentStatus paymentStatus,
                                   long total, long penalty) {
        return EquipmentRental.builder()
                .status(status)
                .paymentStatus(paymentStatus)
                .totalAmount(total)
                .totalPenaltyAmount(penalty)
                .programStartDate(LocalDate.now())
                .programEndDate(LocalDate.now().plusDays(3))
                .build();
    }

    private User renter() {
        return User.builder().id(1L).username("renter").email("renter@test.com").build();
    }
}
