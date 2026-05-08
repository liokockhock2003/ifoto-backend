package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EquipmentRentalRepository extends JpaRepository<EquipmentRental, Long> {

    List<EquipmentRental> findByRenterOrderByCreatedAtDesc(User renter);

    List<EquipmentRental> findAllByOrderByCreatedAtDesc();

    List<EquipmentRental> findByStatusAndDueReturnDateBefore(RentalStatus status, LocalDate date);

    List<EquipmentRental> findByStatusAndApprovedStartDateLessThanEqual(RentalStatus status, LocalDate date);

    List<EquipmentRental> findByStatus(RentalStatus status);
}
