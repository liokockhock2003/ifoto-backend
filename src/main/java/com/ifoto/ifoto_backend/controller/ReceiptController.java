package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.ReceiptDTO.InvoiceResponse;
import com.ifoto.ifoto_backend.dto.ReceiptDTO.ReceiptItemResponse;
import com.ifoto.ifoto_backend.dto.ReceiptDTO.ReceiptResponse;
import com.ifoto.ifoto_backend.dto.ReceiptDTO.ReceiptSubItemResponse;
import com.ifoto.ifoto_backend.model.EquipmentRentalItem;
import com.ifoto.ifoto_backend.model.EquipmentRentalSubItem;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/invoice/rental/{rentalId}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable Long rentalId, Authentication auth) {
        return ResponseEntity.ok(toInvoiceResponse(receiptService.getInvoice(rentalId, auth.getName())));
    }

    @GetMapping("/overdue-invoice/rental/{rentalId}")
    public ResponseEntity<InvoiceResponse> getOverdueInvoice(
            @PathVariable Long rentalId, Authentication auth) {
        return ResponseEntity.ok(toInvoiceResponse(receiptService.getOverdueInvoice(rentalId, auth.getName())));
    }

    @GetMapping("/receipt/rental/{rentalId}")
    public ResponseEntity<ReceiptResponse> getReceipt(
            @PathVariable Long rentalId, Authentication auth) {
        return ResponseEntity.ok(toResponse(receiptService.getReceipt(rentalId, auth.getName())));
    }

    @GetMapping("/overdue-receipt/rental/{rentalId}")
    public ResponseEntity<ReceiptResponse> getOverdueReceipt(
            @PathVariable Long rentalId, Authentication auth) {
        return ResponseEntity.ok(toResponse(receiptService.getOverdueReceipt(rentalId, auth.getName())));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private InvoiceResponse toInvoiceResponse(Receipt r) {
        var rental = r.getEquipmentRental();
        var user = r.getUser();
        return new InvoiceResponse(
                r.getDocumentType().prefix() + r.getReceiptNumber(),
                r.getDocumentType().name(),
                r.getIssuedAt(),
                new ReceiptResponse.RenterInfo(user.getUsername(), user.getFullName(), user.getEmail(), user.getPhoneNumber()),
                new ReceiptResponse.RentalInfo(
                        rental.getRentalNumber(),
                        rental.getApprovedStartDate(),
                        rental.getApprovedEndDate(),
                        rental.getDurationDays(),
                        rental.getTotalBaseAmount(),
                        rental.getTotalPenaltyAmount(),
                        rental.getTotalAmount(),
                        rental.getItems().stream().map(this::toItemResponse).toList(),
                        rental.getSubItems().stream().map(this::toSubItemResponse).toList()
                )
        );
    }

    private ReceiptResponse toResponse(Receipt r) {
        var rental = r.getEquipmentRental();
        var payment = r.getPayment();
        var user = r.getUser();
        return new ReceiptResponse(
                r.getDocumentType().prefix() + r.getReceiptNumber(),
                r.getIssuedAt(),
                new ReceiptResponse.RenterInfo(user.getUsername(), user.getFullName(), user.getEmail(), user.getPhoneNumber()),
                new ReceiptResponse.RentalInfo(
                        rental.getRentalNumber(),
                        rental.getApprovedStartDate(),
                        rental.getApprovedEndDate(),
                        rental.getDurationDays(),
                        rental.getTotalBaseAmount(),
                        rental.getTotalPenaltyAmount(),
                        rental.getTotalAmount(),
                        rental.getItems().stream().map(this::toItemResponse).toList(),
                        rental.getSubItems().stream().map(this::toSubItemResponse).toList()
                ),
                new ReceiptResponse.PaymentInfo(
                        payment.getPaymentType().name(),
                        payment.getPaidAt(),
                        payment.getTransactionId(),
                        payment.getPaymentChannel()
                )
        );
    }

    private ReceiptItemResponse toItemResponse(EquipmentRentalItem item) {
        return new ReceiptItemResponse(
                item.getMainEquipment().getEquipmentType(),
                item.getMainEquipment().getBrand(),
                item.getMainEquipment().getModel(),
                item.getMainEquipment().getSerialNumber(),
                item.getBaseAmount(),
                item.getLatePenaltyAmount(),
                item.getItemTotalAmount()
        );
    }

    private ReceiptSubItemResponse toSubItemResponse(EquipmentRentalSubItem sub) {
        var se = sub.getSubEquipment();
        return new ReceiptSubItemResponse(
                se.getType(),
                se.getEquipmentType(),
                se.getBrand(),
                sub.getBorrowedQuantity(),
                sub.getBaseAmount(),
                sub.getLatePenaltyAmount(),
                sub.getItemTotalAmount()
        );
    }
}
