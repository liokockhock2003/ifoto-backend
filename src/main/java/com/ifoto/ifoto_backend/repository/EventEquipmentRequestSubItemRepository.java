package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.EventEquipmentRequestSubItem;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface EventEquipmentRequestSubItemRepository extends JpaRepository<EventEquipmentRequestSubItem, Long> {

    @Query("""
        SELECT COALESCE(SUM(si.borrowedQuantity), 0)
        FROM EventEquipmentRequestSubItem si JOIN si.eventEquipmentRequest er
        WHERE si.subEquipment.subEquipmentId = :subEquipmentId
        AND er.status IN :statuses
        AND :startDate <= COALESCE(er.approvedEndDate, er.requestedEndDate)
        AND :endDate   >= COALESCE(er.approvedStartDate, er.requestedStartDate)
        AND er.id <> :excludeRequestId
    """)
    int sumCommittedQuantity(
            @Param("subEquipmentId") Long subEquipmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<EventEquipmentRequestStatus> statuses,
            @Param("excludeRequestId") Long excludeRequestId
    );

    @Query("""
        SELECT si.subEquipment.subEquipmentId, COALESCE(SUM(si.borrowedQuantity), 0)
        FROM EventEquipmentRequestSubItem si JOIN si.eventEquipmentRequest er
        WHERE er.status IN :statuses
        AND :startDate <= COALESCE(er.approvedEndDate, er.requestedEndDate)
        AND :endDate   >= COALESCE(er.approvedStartDate, er.requestedStartDate)
        GROUP BY si.subEquipment.subEquipmentId
    """)
    List<Object[]> sumCommittedQuantityPerSubEquipment(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<EventEquipmentRequestStatus> statuses
    );
}
