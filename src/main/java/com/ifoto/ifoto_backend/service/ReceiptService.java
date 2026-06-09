package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.event.ReceiptReadyEvent;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.DocumentType;
import com.ifoto.ifoto_backend.repository.EquipmentRentalRepository;
import com.ifoto.ifoto_backend.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final EquipmentRentalRepository rentalRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;

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
        if (receiptRepository.findByEquipmentRentalIdAndDocumentType(rental.getId(), DocumentType.OVERDUE_INVOICE)
                .isPresent()) {
            return null;
        }
        return buildAndSave(rental, null, DocumentType.OVERDUE_INVOICE);
    }

    @Transactional
    public Receipt createOverdueReceipt(EquipmentRental rental, Payment payment) {
        return buildAndSave(rental, payment, DocumentType.OVERDUE_RECEIPT);
    }

    @Transactional
    public void deleteInvoice(Long rentalId) {
        receiptRepository.findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.INVOICE)
                .ifPresent(receiptRepository::delete);
    }

    private Receipt buildAndSave(EquipmentRental rental, Payment payment, DocumentType documentType) {
        Receipt receipt = Receipt.builder()
                .equipmentRental(rental)
                .payment(payment)
                .user(rental.getRenter())
                .documentType(documentType)
                .issuedAt(LocalDateTime.now())
                .build();
        receipt.setReceiptNumber("TEMP");
        receipt = receiptRepository.save(receipt);

        // All documents for the same rental share the same base number.
        // The first document (INVOICE) uses its own row ID; subsequent ones parse
        // the number from the existing receipt_number (strip 1-char prefix + "111111").
        Long docNumber = receiptRepository
                .findFirstByEquipmentRentalIdAndReceiptNumberNotOrderByIdAsc(rental.getId(), "TEMP")
                .map(r -> Long.parseLong(r.getReceiptNumber().substring(7)))
                .orElse(receipt.getId());

        receipt.setReceiptNumber(documentType.prefix() + "111111" + String.format("%04d", docNumber));
        receipt = receiptRepository.save(receipt);

        eventPublisher.publishEvent(
                new ReceiptReadyEvent(this, rental.getId(), documentType,
                        receipt.getReceiptNumber()));
        return receipt;
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
        Receipt receipt = receiptRepository
                .findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.OVERDUE_INVOICE)
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
        Receipt receipt = receiptRepository
                .findByEquipmentRentalIdAndDocumentType(rentalId, DocumentType.OVERDUE_RECEIPT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Penalty receipt not found for rental: " + rentalId));
        checkAccess(receipt, username);
        return receipt;
    }

    public void checkRentalAccess(Long rentalId, String username) {
        EquipmentRental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rental not found"));
        User requester = userService.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean isOwner = rental.getRenter().getId().equals(requester.getId());
        boolean isCommittee = requester.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));
        if (!isOwner && !isCommittee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    @Transactional(readOnly = true)
    public List<Receipt> findExistingReceipts(Long rentalId) {
        return Arrays.stream(DocumentType.values())
                .flatMap(type -> receiptRepository.findByEquipmentRentalIdAndDocumentType(rentalId, type).stream())
                .toList();
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
