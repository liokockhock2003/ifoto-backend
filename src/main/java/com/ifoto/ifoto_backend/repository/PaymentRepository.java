package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByEquipmentRentalId(Long equipmentRentalId);

    Optional<Payment> findTopByEquipmentRentalIdOrderByCreatedAtDesc(Long equipmentRentalId);

    Optional<Payment> findByBillId(String billId);
}
