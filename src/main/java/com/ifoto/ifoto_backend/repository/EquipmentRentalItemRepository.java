package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.BookedDateRange;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.RentalConflictRow;
import com.ifoto.ifoto_backend.dto.ReportDTO.EquipmentUtilizationProjection;
import com.ifoto.ifoto_backend.model.EquipmentRentalItem;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EquipmentRentalItemRepository extends JpaRepository<EquipmentRentalItem, Long> {

        List<EquipmentRentalItem> findByEquipmentRentalId(Long equipmentRentalId);

        // Conflict check — uses pickupDatetime/returnDatetime (with buffer already applied by caller).
        // Strict < / > so exact same-datetime back-to-back rentals are not considered conflicting.
        @Query("""
                        SELECT CASE WHEN COUNT(eri) > 0 THEN TRUE ELSE FALSE END
                        FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
                        WHERE eri.mainEquipment.mainEquipmentId = :equipmentId
                        AND er.id <> :excludeRentalId
                        AND er.status IN :statuses
                        AND :effectiveStart < er.returnDatetime
                        AND :effectiveEnd   > er.pickupDatetime
                        """)
        boolean existsConflictingApprovedRental(
                        @Param("equipmentId") Long equipmentId,
                        @Param("effectiveStart") LocalDateTime effectiveStart,
                        @Param("effectiveEnd") LocalDateTime effectiveEnd,
                        @Param("excludeRentalId") Long excludeRentalId,
                        @Param("statuses") Collection<RentalStatus> statuses);

        @Query("""
                        SELECT eri.mainEquipment.mainEquipmentId,
                               er.pickupDatetime, er.returnDatetime,
                               er.programStartDate, er.programEndDate,
                               CASE WHEN er.status = :pendingStatus THEN TRUE ELSE FALSE END
                        FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
                        WHERE eri.mainEquipment.mainEquipmentId IN :equipmentIds
                        AND er.status IN :statuses
                        """)
        List<Object[]> findBookedDateRangesRaw(
                        @Param("equipmentIds") List<Long> equipmentIds,
                        @Param("statuses") Collection<RentalStatus> statuses,
                        @Param("pendingStatus") RentalStatus pendingStatus);

        default List<BookedDateRange> findBookedDateRanges(
                        List<Long> equipmentIds,
                        Collection<RentalStatus> statuses,
                        RentalStatus pendingStatus) {
                return findBookedDateRangesRaw(equipmentIds, statuses, pendingStatus)
                                .stream()
                                .map(row -> new BookedDateRange(
                                                (Long) row[0],
                                                row[1] != null ? ((LocalDateTime) row[1]).toLocalDate() : (LocalDate) row[3],
                                                row[2] != null ? ((LocalDateTime) row[2]).toLocalDate() : (LocalDate) row[4],
                                                (Boolean) row[5]
                                ))
                                .toList();
        }

        @Query("""
                        SELECT DISTINCT eri.mainEquipment.mainEquipmentId
                        FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
                        WHERE eri.mainEquipment.mainEquipmentId IN :ids
                        AND er.status IN :statuses
                        AND :today <= COALESCE(cast(er.returnDatetime as date), er.programEndDate)
                        AND :today >= COALESCE(cast(er.pickupDatetime as date), er.programStartDate)
                        """)
        List<Long> findBookedEquipmentIds(
                        @Param("ids") List<Long> ids,
                        @Param("today") LocalDate today,
                        @Param("statuses") Collection<RentalStatus> statuses);

        @Query("""
                        SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.RentalConflictRow(
                               eri.mainEquipment.mainEquipmentId, er.id,
                               er.returnDatetime, er.pickupDatetime,
                               er.programStartDate, er.programEndDate)
                        FROM EquipmentRentalItem eri JOIN eri.equipmentRental er
                        WHERE eri.mainEquipment.mainEquipmentId IN :ids
                        AND er.status IN :statuses
                        AND er.id <> :excludeRentalId
                        AND :startDate <= COALESCE(cast(er.returnDatetime as date), er.programEndDate)
                        AND :endDate   >= COALESCE(cast(er.pickupDatetime as date), er.programStartDate)
                        """)
        List<RentalConflictRow> findRentalConflictsForEquipment(
                        @Param("ids") List<Long> ids,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("statuses") Collection<RentalStatus> statuses,
                        @Param("excludeRentalId") Long excludeRentalId);

        @Query("""
                        SELECT i.mainEquipment.mainEquipmentId AS equipmentId,
                               i.mainEquipment.brand            AS brand,
                               i.mainEquipment.model            AS model,
                               i.mainEquipment.equipmentType    AS equipmentType,
                               COUNT(i)                         AS rentalCount
                        FROM EquipmentRentalItem i
                        GROUP BY i.mainEquipment.mainEquipmentId, i.mainEquipment.brand,
                                 i.mainEquipment.model, i.mainEquipment.equipmentType
                        ORDER BY COUNT(i) DESC
                        """)
        List<EquipmentUtilizationProjection> equipmentUtilization();
}
