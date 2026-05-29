package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.EventEquipmentRequestItem;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventEquipmentRequestItemRepository extends JpaRepository<EventEquipmentRequestItem, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END
            FROM EventEquipmentRequestItem i JOIN i.eventEquipmentRequest r
            WHERE i.mainEquipment.mainEquipmentId = :equipmentId
              AND r.id <> :excludeRequestId
              AND r.status IN :statuses
              AND :startDate <= r.approvedEndDate
              AND :endDate >= r.approvedStartDate
            """)
    boolean existsConflictingRequest(
            @Param("equipmentId") Long equipmentId,
            @Param("excludeRequestId") Long excludeRequestId,
            @Param("statuses") List<EventEquipmentRequestStatus> statuses,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT DISTINCT i.mainEquipment.mainEquipmentId
            FROM EventEquipmentRequestItem i JOIN i.eventEquipmentRequest r
            WHERE i.mainEquipment.mainEquipmentId IN :ids
            AND r.status IN :statuses
            AND :today <= r.approvedEndDate
            AND :today >= r.approvedStartDate
            """)
    List<Long> findBookedEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("today") LocalDate today,
            @Param("statuses") List<EventEquipmentRequestStatus> statuses);
}
