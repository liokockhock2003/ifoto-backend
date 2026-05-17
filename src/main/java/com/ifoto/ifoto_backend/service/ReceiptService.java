package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.Payment;
import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.repository.ReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final UserService userService;

    @Transactional
    public Receipt createReceipt(EquipmentRental rental, Payment payment) {
        Receipt receipt = Receipt.builder()
                .equipmentRental(rental)
                .payment(payment)
                .user(rental.getRenter())
                .issuedAt(LocalDateTime.now())
                .build();
        receipt.setReceiptNumber("T" + System.currentTimeMillis());
        receipt = receiptRepository.save(receipt);
        int year = receipt.getIssuedAt().getYear();
        receipt.setReceiptNumber("KFK-%d-%06d".formatted(year, receipt.getId()));
        return receiptRepository.save(receipt);
    }

    @Transactional(readOnly = true)
    public Receipt getReceipt(String receiptNumber, String username) {
        Receipt receipt = receiptRepository.findByReceiptNumber(receiptNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Receipt not found: " + receiptNumber));

        User requester = userService.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean isOwner = receipt.getUser().getId().equals(requester.getId());
        boolean isCommittee = requester.getRoles().stream()
                .anyMatch(r -> r.getName().equals("ROLE_EQUIPMENT_COMMITTEE"));

        if (!isOwner && !isCommittee) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return receipt;
    }

    @Transactional(readOnly = true)
    public List<Receipt> getMyReceipts(String username) {
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return receiptRepository.findByUserOrderByIssuedAtDesc(user);
    }
}
