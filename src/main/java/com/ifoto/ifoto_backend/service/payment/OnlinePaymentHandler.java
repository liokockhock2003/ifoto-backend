package com.ifoto.ifoto_backend.service.payment;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.PaymentRecordStatus;
import com.ifoto.ifoto_backend.model.enumerator.PaymentType;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentMethod;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.PaymentRepository;
import com.ifoto.ifoto_backend.service.BillplzService;
import com.ifoto.ifoto_backend.service.BillplzService.BillplzBillResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class OnlinePaymentHandler implements PaymentMethodHandler {

    private final BillplzService billplzService;
    private final PaymentRepository paymentRepository;
    private final BillplzConfig billplzConfig;

    @Override
    public RentalPaymentMethod getMethod() {
        return RentalPaymentMethod.ONLINE;
    }

    @Override
    public void initiate(EquipmentRental rental, User renter) {
        if (rental.getPaymentStatus() == RentalPaymentStatus.PENALTY_PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Penalty has already been paid");
        }

        boolean isPenaltyPayment = rental.getStatus() == RentalStatus.RETURNED
                && rental.getTotalPenaltyAmount() != null && rental.getTotalPenaltyAmount() > 0;

        // Allow re-calling when payment is already pending — return existing bill URL
        if (rental.getStatus() == RentalStatus.PENDING_PAYMENT) return;

        if (rental.getStatus() != RentalStatus.APPROVED && !isPenaltyPayment) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rental must be APPROVED to initiate payment");
        }

        long chargeAmount = isPenaltyPayment ? rental.getTotalPenaltyAmount() : rental.getTotalAmount();

        Payment payment = Payment.builder()
                .equipmentRental(rental)
                .user(renter)
                .amount(chargeAmount)
                .paymentType(PaymentType.ONLINE)
                .status(PaymentRecordStatus.PENDING)
                .collectionId(billplzConfig.getCollectionId())
                .build();
        payment = paymentRepository.save(payment);

        BillplzBillResponse bill = billplzService.createBill(rental, renter, chargeAmount);
        payment.setBillId(bill.billId());
        payment.setBillUrl(bill.billUrl());
        payment.setDueAt(bill.dueAt());
        paymentRepository.save(payment);

        rental.setPaymentMethod(RentalPaymentMethod.ONLINE);
        rental.setPaymentStatus(RentalPaymentStatus.ONLINE_PENDING);
        rental.setStatus(RentalStatus.PENDING_PAYMENT);
    }
}
