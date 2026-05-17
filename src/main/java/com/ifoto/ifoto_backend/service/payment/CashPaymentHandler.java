package com.ifoto.ifoto_backend.service.payment;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.PaymentRecordStatus;
import com.ifoto.ifoto_backend.model.enumerator.PaymentType;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentMethod;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.PaymentRepository;
import com.ifoto.ifoto_backend.repository.UserRepository;
import com.ifoto.ifoto_backend.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CashPaymentHandler implements PaymentMethodHandler {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final MailService mailService;

    @Override
    public RentalPaymentMethod getMethod() {
        return RentalPaymentMethod.CASH;
    }

    @Override
    public void initiate(EquipmentRental rental, User renter) {
        if (rental.getPaymentStatus() == RentalPaymentStatus.PENALTY_PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Penalty has already been paid");
        }

        boolean isPenaltyPayment = rental.getStatus() == RentalStatus.RETURNED
                && rental.getTotalPenaltyAmount() != null && rental.getTotalPenaltyAmount() > 0;
        if (rental.getStatus() != RentalStatus.APPROVED && !isPenaltyPayment) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rental must be APPROVED to initiate payment");
        }

        long chargeAmount = isPenaltyPayment ? rental.getTotalPenaltyAmount() : rental.getTotalAmount();

        Payment payment = Payment.builder()
                .equipmentRental(rental)
                .user(renter)
                .amount(chargeAmount)
                .paymentType(PaymentType.CASH)
                .status(PaymentRecordStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        rental.setPaymentMethod(RentalPaymentMethod.CASH);
        rental.setPaymentStatus(RentalPaymentStatus.CASH_PENDING);
        rental.setStatus(RentalStatus.PENDING_PAYMENT);

        List<String> committeeEmails = userRepository.findAllByRoleName("ROLE_EQUIPMENT_COMMITTEE")
                .stream().map(User::getEmail).toList();
        if (!committeeEmails.isEmpty()) {
            mailService.sendCashPaymentPendingToCommittee(committeeEmails, rental.getRentalNumber(),
                    renter.getFullName() != null ? renter.getFullName() : renter.getUsername(),
                    rental.getTotalAmount());
        }
    }
}
