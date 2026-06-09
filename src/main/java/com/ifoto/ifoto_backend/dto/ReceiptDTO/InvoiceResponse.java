package com.ifoto.ifoto_backend.dto.ReceiptDTO;

import java.time.LocalDateTime;

public record InvoiceResponse(
        String invoiceNumber,
        String documentType,
        LocalDateTime issuedAt,
        ReceiptResponse.RenterInfo renter,
        ReceiptResponse.RentalInfo rental,
        CommitteeInfo committee
) {
    public record CommitteeInfo(String bankName, String accountNo, String accountName, String signature) {}
}
