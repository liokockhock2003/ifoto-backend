package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.BookedDateRange;
import com.ifoto.ifoto_backend.model.EquipmentRentalItem;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface EquipmentRentalItemRepository extends JpaRepository<EquipmentRentalItem, Long> {

        List<EquipmentRentalItem> findByEquipmentRentalId(Long equipmentRentalId);

        // Used for both submission (excludeRentalId=0L) and approval; checks against
        // confirmed approved dates only
        @Query("""
                        SELECT CASE WHEN COUNT(eri) > 0 THEN TRUE ELSE FALSE END
                        FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
                        WHERE eri.mainEquipment.mainEquipmentId = :equipmentId
                        AND er.id <> :excludeRentalId
                        AND er.status IN :statuses
                        AND :startDate <= er.approvedEndDate
                        AND :endDate   >= er.approvedStartDate
                        """)
        boolean existsConflictingApprovedRental(
                        @Param("equipmentId") Long equipmentId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("excludeRentalId") Long excludeRentalId,
                        @Param("statuses") Collection<RentalStatus> statuses);

        // Returns booked date ranges for a set of equipment IDs (used by rentable
        // equipment listing).
        // Pass PENDING_REVIEW in statuses to include soft (pending) blocks alongside
        // confirmed ones.
        // pending=true means the dates are from a PENDING_REVIEW rental (soft indicator
        // for frontend).
        @Query("""
                        SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.BookedDateRange(
                            eri.mainEquipment.mainEquipmentId,
                            COALESCE(er.approvedStartDate, er.requestedStartDate),
                            COALESCE(er.approvedEndDate, er.requestedEndDate),
                            CASE WHEN er.status = :pendingStatus THEN TRUE ELSE FALSE END
                        )
                        FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
                        WHERE eri.mainEquipment.mainEquipmentId IN :equipmentIds
                        AND er.status IN :statuses
                        """)
        List<BookedDateRange> findBookedDateRanges(
                        @Param("equipmentIds") List<Long> equipmentIds,
                        @Param("statuses") Collection<RentalStatus> statuses,
                        @Param("pendingStatus") RentalStatus pendingStatus);
}
