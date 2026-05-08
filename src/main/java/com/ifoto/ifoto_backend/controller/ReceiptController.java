package com.ifoto.ifoto_backend.controller;

import com.ifoto.ifoto_backend.dto.ReceiptDTO.ReceiptItemResponse;
import com.ifoto.ifoto_backend.dto.ReceiptDTO.ReceiptResponse;
import com.ifoto.ifoto_backend.dto.ReceiptDTO.ReceiptSummaryResponse;
import com.ifoto.ifoto_backend.model.EquipmentRentalItem;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping("/{receiptNumber}")
    public ResponseEntity<ReceiptResponse> getReceipt(
            @PathVariable String receiptNumber, Authentication auth) {
        return ResponseEntity.ok(toResponse(receiptService.getReceipt(receiptNumber, auth.getName())));
    }

    @GetMapping("/my")
    public ResponseEntity<List<ReceiptSummaryResponse>> getMyReceipts(Authentication auth) {
        return ResponseEntity.ok(receiptService.getMyReceipts(auth.getName())
                .stream().map(this::toSummary).toList());
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private ReceiptResponse toResponse(Receipt r) {
        var rental = r.getEquipmentRental();
        var payment = r.getPayment();

        return new ReceiptResponse(
                r.getReceiptNumber(),
                r.getIssuedAt(),
                new ReceiptResponse.RenterInfo(
                        r.getUser().getUsername(),
                        r.getUser().getFullName(),
                        r.getUser().getEmail()
                ),
                new ReceiptResponse.RentalInfo(
                        rental.getRentalNumber(),
                        rental.getApprovedStartDate(),
                        rental.getApprovedEndDate(),
                        rental.getDurationDays(),
                        rental.getTotalBaseAmount(),
                        rental.getTotalPenaltyAmount(),
                        rental.getTotalAmount(),
                        rental.getItems().stream().map(this::toItemResponse).toList()
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
                item.getPricingCategory(),
                item.getBaseAmount(),
                item.getLatePenaltyAmount(),
                item.getItemTotalAmount()
        );
    }

    private ReceiptSummaryResponse toSummary(Receipt r) {
        return new ReceiptSummaryResponse(
                r.getReceiptNumber(),
                r.getIssuedAt(),
                r.getEquipmentRental().getRentalNumber(),
                r.getEquipmentRental().getTotalAmount(),
                r.getPayment().getPaymentType().name()
        );
    }
}
