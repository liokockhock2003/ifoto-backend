package com.ifoto.ifoto_backend.dto.ReceiptDTO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ReceiptResponse(
        String receiptNumber,
        LocalDateTime issuedAt,
        RenterInfo renter,
        RentalInfo rental,
        PaymentInfo payment,
        CommitteeInfo committee
) {
    public record RenterInfo(String username, String fullName, String email, String phoneNumber) {}

    public record CommitteeInfo(String approvedBy, String signature) {}

    public record RentalInfo(
            String rentalNumber,
            LocalDate programStartDate,
            LocalDate programEndDate,
            Integer durationDays,
            Long totalBaseAmount,
            Long totalPenaltyAmount,
            Long totalAmount,
            List<ReceiptItemResponse> items,
            List<ReceiptSubItemResponse> subItems
    ) {}

    public record PaymentInfo(
            String paymentType,
            LocalDateTime paidAt,
            String transactionId,
            String paymentChannel
    ) {}
}
