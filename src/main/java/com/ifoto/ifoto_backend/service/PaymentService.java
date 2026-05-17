package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.config.BillplzConfig;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.PaymentRecordStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import com.ifoto.ifoto_backend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final BillplzXSignatureService xSignatureService;
    private final ReceiptService receiptService;
    private final MailService mailService;
    private final BillplzConfig billplzConfig;

    @Transactional
    public void handleBillplzCallback(Map<String, String> params) {
        log.info("Billplz callback received: {}", params);
        String receivedSig = params.get("x_signature");
        if (!xSignatureService.verify(params, receivedSig)) {
            log.warn("Invalid Billplz x_signature — received={}, params={}", receivedSig, params);
            return;
        }

        String billId = params.get("id");
        Payment payment = paymentRepository.findByBillId(billId).orElse(null);
        if (payment == null) {
            log.warn("No payment found for Billplz bill_id: {}", billId);
            return;
        }

        log.info("Callback paid={} state={}", params.get("paid"), params.get("state"));
        if ("true".equals(params.get("paid")) && "paid".equals(params.get("state"))) {
            log.info("Payment {} marked PAID", payment.getId());
            payment.setStatus(PaymentRecordStatus.PAID);
            payment.setPaidAmount(payment.getAmount());
            payment.setPaidAt(LocalDateTime.now());
            payment.setTransactionId(params.get("transaction_id"));
            payment.setPaymentChannel(params.get("payment_method"));
            payment.setTransactionStatus(params.get("state"));
            paymentRepository.save(payment);

            EquipmentRental rental = payment.getEquipmentRental();
            log.info("Rental {} current status={}, paymentStatus={}", rental.getRentalNumber(), rental.getStatus(), rental.getPaymentStatus());
            boolean isPenalty = rental.getTotalPenaltyAmount() != null && rental.getTotalPenaltyAmount() > 0;
            if (isPenalty) {
                log.info("Penalty payment confirmed for rental {}", rental.getRentalNumber());
                rental.setStatus(RentalStatus.RETURNED);
                rental.setPaymentStatus(RentalPaymentStatus.PENALTY_PAID);
            } else {
                log.info("Normal payment confirmed for rental {}, setting PAID", rental.getRentalNumber());
                rental.setStatus(RentalStatus.PAID);
                rental.setPaidAt(LocalDateTime.now());
                rental.setPaymentStatus(RentalPaymentStatus.ONLINE_PAID);
            }

            Receipt receipt = receiptService.createReceipt(rental, payment);
            mailService.sendPaymentConfirmedToRenter(
                    rental.getRenter().getEmail(), rental.getRentalNumber(), receipt.getReceiptNumber());

        } else if ("false".equals(params.get("paid"))) {
            log.info("Payment {} marked FAILED", payment.getId());
            payment.setStatus(PaymentRecordStatus.FAILED);
            payment.setTransactionStatus(params.get("state"));
            paymentRepository.save(payment);
        } else {
            log.warn("Unhandled callback state — paid={}, state={}", params.get("paid"), params.get("state"));
        }
    }

    public String handleBillplzRedirect(Map<String, String> params) {
        String base = billplzConfig.getRedirectUrl();

        // Billplz redirect uses billplz[key] bracket notation
        String receivedSig = params.get("billplz[x_signature]");
        Map<String, String> signedParams = new java.util.HashMap<>(params);
        signedParams.remove("billplz[x_signature]");

        if (!xSignatureService.verify(signedParams, receivedSig)) {
            log.warn("Invalid Billplz redirect x_signature");
            return base + "?status=error";
        }

        String billId = params.get("billplz[id]");
        if ("true".equals(params.get("billplz[paid]"))) {
            return base + "?status=success&billId=" + billId;
        }
        return base + "?status=failed&billId=" + billId;
    }

    @Transactional
    public void confirmCashPayment(EquipmentRental rental, User committeeUser) {
        Payment payment = paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(rental.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No payment record found for rental " + rental.getRentalNumber()));

        payment.setStatus(PaymentRecordStatus.PAID);
        payment.setPaidAmount(payment.getAmount());
        payment.setPaidAt(LocalDateTime.now());
        payment.setConfirmedBy(committeeUser);
        payment.setConfirmedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        boolean isPenalty = rental.getTotalPenaltyAmount() != null && rental.getTotalPenaltyAmount() > 0;
        if (isPenalty) {
            rental.setStatus(RentalStatus.RETURNED);
            rental.setPaymentStatus(RentalPaymentStatus.PENALTY_PAID);
        } else {
            rental.setStatus(RentalStatus.PAID);
            rental.setPaidAt(LocalDateTime.now());
            rental.setPaymentStatus(RentalPaymentStatus.CASH_PAID);
        }

        Receipt receipt = receiptService.createReceipt(rental, payment);
        mailService.sendPaymentConfirmedToRenter(
                rental.getRenter().getEmail(), rental.getRentalNumber(), receipt.getReceiptNumber());
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Payment> findByRentalId(Long rentalId) {
        return paymentRepository.findTopByEquipmentRentalIdOrderByCreatedAtDesc(rentalId);
    }
}
