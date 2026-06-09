package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentBookedRange;
import com.ifoto.ifoto_backend.model.EquipmentRentalSubItem;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EquipmentRentalSubItemRepository extends JpaRepository<EquipmentRentalSubItem, Long> {

    List<EquipmentRentalSubItem> findByEquipmentRentalId(Long rentalId);

    @Query("""
            SELECT ersi.subEquipment.subEquipmentId,
                   er.pickupDatetime, er.returnDatetime,
                   er.programStartDate, er.programEndDate,
                   ersi.borrowedQuantity,
                   CASE WHEN er.status = :pendingStatus THEN TRUE ELSE FALSE END
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE ersi.subEquipment.subEquipmentId IN :subEquipmentIds
            AND er.status IN :statuses
            """)
    List<Object[]> findBookedQuantityRangesRaw(
            @Param("subEquipmentIds") List<Long> subEquipmentIds,
            @Param("statuses") Collection<RentalStatus> statuses,
            @Param("pendingStatus") RentalStatus pendingStatus
    );

    default List<SubEquipmentBookedRange> findBookedQuantityRanges(
            List<Long> subEquipmentIds,
            Collection<RentalStatus> statuses,
            RentalStatus pendingStatus
    ) {
        return findBookedQuantityRangesRaw(subEquipmentIds, statuses, pendingStatus)
                .stream()
                .map(row -> new SubEquipmentBookedRange(
                        (Long) row[0],
                        row[1] != null ? ((LocalDateTime) row[1]).toLocalDate() : (LocalDate) row[3],
                        row[2] != null ? ((LocalDateTime) row[2]).toLocalDate() : (LocalDate) row[4],
                        ((Number) row[5]).intValue(),
                        (Boolean) row[6]
                ))
                .toList();
    }

    @Query("""
            SELECT COALESCE(SUM(ersi.borrowedQuantity), 0)
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE ersi.subEquipment.subEquipmentId = :subEquipmentId
            AND er.status IN :statuses
            AND :startDatetime < COALESCE(er.returnDatetime, cast(er.programEndDate as timestamp))
            AND :endDatetime   > COALESCE(er.pickupDatetime, cast(er.programStartDate as timestamp))
            """)
    int sumCommittedQuantity(
            @Param("subEquipmentId") Long subEquipmentId,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime,
            @Param("statuses") Collection<RentalStatus> statuses
    );

    @Query("""
            SELECT ersi.subEquipment.subEquipmentId, COALESCE(SUM(ersi.borrowedQuantity), 0)
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE er.status IN :statuses
            AND er.id <> :excludeRentalId
            AND :startDatetime < COALESCE(er.returnDatetime, cast(er.programEndDate as timestamp))
            AND :endDatetime   > COALESCE(er.pickupDatetime, cast(er.programStartDate as timestamp))
            GROUP BY ersi.subEquipment.subEquipmentId
            """)
    List<Object[]> sumCommittedQuantityPerSubEquipment(
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime,
            @Param("statuses") Collection<RentalStatus> statuses,
            @Param("excludeRentalId") Long excludeRentalId
    );

    @Query("""
            SELECT ersi.subEquipment.subEquipmentId, er.id, er.returnDatetime, SUM(ersi.borrowedQuantity)
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE ersi.subEquipment.subEquipmentId IN :ids
            AND er.status IN :statuses
            AND er.id <> :excludeRentalId
            AND cast(er.returnDatetime as date) = cast(:boundaryDate as date)
            GROUP BY ersi.subEquipment.subEquipmentId, er.id, er.returnDatetime
            """)
    List<Object[]> findBoundaryReturnQuantities(
            @Param("ids") List<Long> ids,
            @Param("boundaryDate") LocalDateTime boundaryDate,
            @Param("statuses") Collection<RentalStatus> statuses,
            @Param("excludeRentalId") Long excludeRentalId
    );

    @Query("""
            SELECT ersi.subEquipment.subEquipmentId, er.id, er.pickupDatetime, SUM(ersi.borrowedQuantity)
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE ersi.subEquipment.subEquipmentId IN :ids
            AND er.status IN :statuses
            AND er.id <> :excludeRentalId
            AND cast(er.pickupDatetime as date) = cast(:boundaryDate as date)
            GROUP BY ersi.subEquipment.subEquipmentId, er.id, er.pickupDatetime
            """)
    List<Object[]> findBoundaryPickupQuantities(
            @Param("ids") List<Long> ids,
            @Param("boundaryDate") LocalDateTime boundaryDate,
            @Param("statuses") Collection<RentalStatus> statuses,
            @Param("excludeRentalId") Long excludeRentalId
    );
}
