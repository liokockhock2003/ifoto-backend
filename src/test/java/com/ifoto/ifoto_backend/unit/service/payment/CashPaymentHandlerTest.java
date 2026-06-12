package com.ifoto.ifoto_backend.unit.service.payment;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.PaymentRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import com.ifoto.ifoto_backend.service.MailService;
import com.ifoto.ifoto_backend.service.payment.CashPaymentHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashPaymentHandlerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private MailService mailService;

    @InjectMocks
    private CashPaymentHandler handler;

    @Test
    void initiate_penaltyAlreadyPaid_throwsBadRequest() {
        EquipmentRental rental = rental(RentalStatus.RETURNED, RentalPaymentStatus.PENALTY_PAID, 1000L, 200L);
        assertThrows400(() -> handler.initiate(rental, renter()));
    }

    @Test
    void initiate_notPickedUpAndNotPenalty_throwsBadRequest() {
        EquipmentRental rental = rental(RentalStatus.APPROVED, RentalPaymentStatus.NONE, 1000L, 0L);
        assertThrows400(() -> handler.initiate(rental, renter()));
    }

    @Test
    void initiate_pickedUpStatus_createsPaymentAndUpdateRental() {
        EquipmentRental rental = rental(RentalStatus.PICKED_UP, RentalPaymentStatus.NONE, 5000L, 0L);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of());

        handler.initiate(rental, renter());

        assertEquals(RentalStatus.PENDING_PAYMENT, rental.getStatus());
        assertEquals(RentalPaymentStatus.CASH_PENDING, rental.getPaymentStatus());
        verify(paymentRepository).save(any());
    }

    @Test
    void initiate_penaltyPayment_usesPenaltyAmount() {
        EquipmentRental rental = rental(RentalStatus.RETURNED, RentalPaymentStatus.NONE, 5000L, 800L);
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of());

        handler.initiate(rental, renter());

        assertEquals(RentalStatus.PENDING_PAYMENT, rental.getStatus());
        assertEquals(RentalPaymentStatus.CASH_PENDING, rental.getPaymentStatus());
    }

    @Test
    void initiate_committeeEmailsExist_sendsNotificationEmail() {
        EquipmentRental rental = rental(RentalStatus.PICKED_UP, RentalPaymentStatus.NONE, 5000L, 0L);
        rental.setRentalNumber("R-0001");
        User committeeUser = User.builder().email("committee@utm.my").build();
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")).thenReturn(List.of(committeeUser));

        User renter = renterWithName("Alice");
        handler.initiate(rental, renter);

        verify(mailService).sendCashPaymentPendingToCommittee(any(), eq("R-0001"), eq("Alice"), any());
    }

    private void assertThrows400(org.junit.jupiter.api.function.Executable exec) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, exec);
        assertEquals(400, ex.getStatusCode().value());
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

    private User renterWithName(String name) {
        return User.builder().id(1L).username("renter").fullName(name).email("renter@test.com").build();
    }
}
