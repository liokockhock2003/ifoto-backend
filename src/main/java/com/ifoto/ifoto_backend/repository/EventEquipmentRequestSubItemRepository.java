package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.EventEquipmentRequestSubItem;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EventEquipmentRequestSubItemRepository extends JpaRepository<EventEquipmentRequestSubItem, Long> {

    @Query("""
        SELECT COALESCE(SUM(si.borrowedQuantity), 0)
        FROM EventEquipmentRequestSubItem si JOIN si.eventEquipmentRequest er
        WHERE si.subEquipment.subEquipmentId = :subEquipmentId
          AND er.status IN :statuses
          AND :startDatetime < COALESCE(er.returnDatetime, er.endDatetime)
          AND :endDatetime   > COALESCE(er.pickupDatetime, er.startDatetime)
          AND er.id <> :excludeRequestId
    """)
    int sumCommittedQuantity(
            @Param("subEquipmentId") Long subEquipmentId,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime,
            @Param("statuses") Collection<EventEquipmentRequestStatus> statuses,
            @Param("excludeRequestId") Long excludeRequestId
    );

    @Query("""
        SELECT si.subEquipment.subEquipmentId, COALESCE(SUM(si.borrowedQuantity), 0)
        FROM EventEquipmentRequestSubItem si JOIN si.eventEquipmentRequest er
        WHERE er.status IN :statuses
          AND :startDatetime < COALESCE(er.returnDatetime, er.endDatetime)
          AND :endDatetime   > COALESCE(er.pickupDatetime, er.startDatetime)
        GROUP BY si.subEquipment.subEquipmentId
    """)
    List<Object[]> sumCommittedQuantityPerSubEquipment(
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime,
            @Param("statuses") Collection<EventEquipmentRequestStatus> statuses
    );

    @Query("""
        SELECT si.subEquipment.subEquipmentId, er.id,
               COALESCE(er.returnDatetime, er.endDatetime), SUM(si.borrowedQuantity)
        FROM EventEquipmentRequestSubItem si JOIN si.eventEquipmentRequest er
        WHERE si.subEquipment.subEquipmentId IN :ids
          AND er.status IN :statuses
          AND cast(COALESCE(er.returnDatetime, er.endDatetime) as date) = cast(:boundaryDate as date)
        GROUP BY si.subEquipment.subEquipmentId, er.id, COALESCE(er.returnDatetime, er.endDatetime)
    """)
    List<Object[]> findBoundaryReturnQuantities(
            @Param("ids") List<Long> ids,
            @Param("boundaryDate") LocalDateTime boundaryDate,
            @Param("statuses") Collection<EventEquipmentRequestStatus> statuses
    );

    @Query("""
        SELECT si.subEquipment.subEquipmentId, er.id,
               COALESCE(er.pickupDatetime, er.startDatetime), SUM(si.borrowedQuantity)
        FROM EventEquipmentRequestSubItem si JOIN si.eventEquipmentRequest er
        WHERE si.subEquipment.subEquipmentId IN :ids
          AND er.status IN :statuses
          AND cast(COALESCE(er.pickupDatetime, er.startDatetime) as date) = cast(:boundaryDate as date)
        GROUP BY si.subEquipment.subEquipmentId, er.id, COALESCE(er.pickupDatetime, er.startDatetime)
    """)
    List<Object[]> findBoundaryPickupQuantities(
            @Param("ids") List<Long> ids,
            @Param("boundaryDate") LocalDateTime boundaryDate,
            @Param("statuses") Collection<EventEquipmentRequestStatus> statuses
    );
}
