package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.DocumentType;
import com.ifoto.ifoto_backend.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final UserService userService;

    @Transactional
    public Receipt createReceipt(EquipmentRental rental, Payment payment) {
        return buildAndSave(rental, payment, DocumentType.RECEIPT);
    }

    @Transactional
    public Receipt createInvoice(EquipmentRental rental) {
        return buildAndSave(rental, null, DocumentType.INVOICE);
    }

    @Transactional
    public Receipt createOverdueInvoice(EquipmentRental rental) {
        if (receiptRepository.findByEquipmentRentalIdAndDocumentType(rental.getId(), DocumentType.OVERDUE_INVOICE).isPresent()) {
            return null;
        }
        return buildAndSave(rental, null, DocumentType.OVERDUE_INVOICE);
    }

    @Transactional
    public Receipt createOverdueReceipt(EquipmentRental rental, Payment payment) {
        return buildAndSave(rental, payment, DocumentType.OVERDUE_RECEIPT);
    }

    private Receipt buildAndSave(EquipmentRental rental, Payment payment, DocumentType documentType) {
        Receipt receipt = Receipt.builder()
                .equipmentRental(rental)
                .payment(payment)
                .user(rental.getRenter())
                .documentType(documentType)
                .issuedAt(LocalDateTime.now())
                .build();
        receipt.setReceiptNumber("T" + System.currentTimeMillis());
        receipt = receiptRepository.save(receipt);
        receipt.setReceiptNumber(String.valueOf(receipt.getId()));
        return receiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Receipt getInvoice(Long rentalId, String username) {
        Receipt receipt = receiptRepository.findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.INVOICE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Invoice not found for rental: " + rentalId));
        checkAccess(receipt, username);
        return receipt;
    }

    @Transactional(readOnly = true)
    public Receipt getOverdueInvoice(Long rentalId, String username) {
        Receipt receipt = receiptRepository.findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.OVERDUE_INVOICE)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Overdue invoice not found for rental: " + rentalId));
        checkAccess(receipt, username);
        return receipt;
    }

    @Transactional(readOnly = true)
    public Receipt getReceipt(Long rentalId, String username) {
        Receipt receipt = receiptRepository.findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.RECEIPT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Receipt not found for rental: " + rentalId));
        checkAccess(receipt, username);
        return receipt;
    }

    @Transactional(readOnly = true)
    public Receipt getOverdueReceipt(Long rentalId, String username) {
        Receipt receipt = receiptRepository.findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.OVERDUE_RECEIPT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Penalty receipt not found for rental: " + rentalId));
        checkAccess(receipt, username);
        return receipt;
    }

    private void checkAccess(Receipt receipt, String username) {
        User requester = userService.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean isOwner = receipt.getUser().getId().equals(requester.getId());
        boolean isCommittee = requester.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
        if (!isOwner && !isCommittee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
