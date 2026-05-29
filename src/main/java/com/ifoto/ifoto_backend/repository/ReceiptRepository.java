package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.Receipt;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    Optional<Receipt> findByReceiptNumber(String receiptNumber);

    List<Receipt> findByUserOrderByIssuedAtDesc(User user);

    Optional<Receipt> findByEquipmentRentalIdAndDocumentType(Long rentalId, DocumentType documentType);
}
