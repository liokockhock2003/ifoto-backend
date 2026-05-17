package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EquipmentRentalRepository extends JpaRepository<EquipmentRental, Long> {

    List<EquipmentRental> findByRenterOrderByCreatedAtDesc(User renter);

    @Query("""
            SELECT r FROM EquipmentRental r
            LEFT JOIN r.renter u
            WHERE (:status IS NULL OR :status = '' OR CAST(r.status AS string) = :status)
              AND (:search IS NULL OR :search = ''
                   OR LOWER(r.rentalNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY r.createdAt DESC
            """)
    Page<EquipmentRental> searchRentals(
            @Param("search") String search,
            @Param("status") String status,
            Pageable pageable);

    List<EquipmentRental> findAllByOrderByCreatedAtDesc();

    List<EquipmentRental> findByStatusAndDueReturnDateBefore(RentalStatus status, LocalDate date);

    List<EquipmentRental> findByStatusAndApprovedStartDateLessThanEqual(RentalStatus status, LocalDate date);

    List<EquipmentRental> findByStatus(RentalStatus status);
}
