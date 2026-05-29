package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.ReportDTO.RentalVolumeProjection;
import com.ifoto.ifoto_backend.dto.ReportDTO.RevenueProjection;
import com.ifoto.ifoto_backend.dto.ReportDTO.StatusBreakdownProjection;
import com.ifoto.ifoto_backend.model.EquipmentRental;
import com.ifoto.ifoto_backend.model.User;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
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

    long countByStatus(RentalStatus status);

    long countByPaymentStatus(RentalPaymentStatus paymentStatus);

    @Query("SELECT COUNT(r) FROM EquipmentRental r WHERE r.createdAt >= :from AND r.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(r.totalBaseAmount), 0) + COALESCE(SUM(r.totalPenaltyAmount), 0) FROM EquipmentRental r WHERE r.paymentStatus IN :statuses")
    long sumRevenue(@Param("statuses") Collection<RentalPaymentStatus> statuses);

    @Query("SELECT r.status AS status, COUNT(r) AS count FROM EquipmentRental r GROUP BY r.status")
    List<StatusBreakdownProjection> countGroupedByStatus();

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m') AS month, COUNT(*) AS count " +
                   "FROM equipment_rentals WHERE created_at >= :since " +
                   "GROUP BY month ORDER BY month ASC", nativeQuery = true)
    List<RentalVolumeProjection> countByMonth(@Param("since") LocalDateTime since);

    @Query(value = "SELECT DATE_FORMAT(paid_at, '%Y-%m') AS month, " +
                   "COALESCE(SUM(total_base_amount), 0) AS baseAmount, " +
                   "COALESCE(SUM(total_penalty_amount), 0) AS penaltyAmount " +
                   "FROM equipment_rentals " +
                   "WHERE paid_at >= :since AND payment_status IN :statuses " +
                   "GROUP BY month ORDER BY month ASC", nativeQuery = true)
    List<RevenueProjection> revenueByMonth(@Param("since") LocalDateTime since, @Param("statuses") List<String> statuses);
}
