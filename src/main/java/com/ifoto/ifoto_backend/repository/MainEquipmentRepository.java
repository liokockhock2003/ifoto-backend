package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.MainEquipment;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface MainEquipmentRepository extends JpaRepository<MainEquipment, Long> {

    List<MainEquipment> findByIsForRentTrue();

    @Query("""
        SELECT e FROM MainEquipment e
        WHERE (:forRentOnly = false OR e.isForRent = true)
        AND NOT EXISTS (
            SELECT 1 FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
            WHERE eri.mainEquipment = e
            AND er.status IN :rentalStatuses
            AND er.id <> :excludeRentalId
            AND :startDate <= COALESCE(cast(er.returnDatetime as date), er.programEndDate)
            AND :endDate   >= COALESCE(cast(er.pickupDatetime as date), er.programStartDate)
        )
        AND NOT EXISTS (
            SELECT 1 FROM EventEquipmentRequestItem i JOIN i.eventEquipmentRequest r
            WHERE i.mainEquipment = e
            AND r.status IN :eventStatuses
            AND :effectiveStart < COALESCE(r.returnDatetime, r.endDatetime)
            AND :effectiveEnd   > COALESCE(r.pickupDatetime, r.startDatetime)
        )
        AND NOT EXISTS (
            SELECT 1 FROM MainEquipmentStatus s
            WHERE s.mainEquipment = e
            AND :effectiveStart < s.endDatetime
            AND :effectiveEnd   > s.startDatetime
        )
    """)
    List<MainEquipment> findAvailableEquipment(
            @Param("forRentOnly") boolean forRentOnly,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("rentalStatuses") Collection<RentalStatus> rentalStatuses,
            @Param("eventStatuses") Collection<EventEquipmentRequestStatus> eventStatuses,
            @Param("effectiveStart") LocalDateTime effectiveStart,
            @Param("effectiveEnd") LocalDateTime effectiveEnd,
            @Param("excludeRentalId") Long excludeRentalId);
}
