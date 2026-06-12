package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.EventConflictRow;
import com.ifoto.ifoto_backend.model.EventEquipmentRequestItem;
import com.ifoto.ifoto_backend.model.enumerator.EventEquipmentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface EventEquipmentRequestItemRepository extends JpaRepository<EventEquipmentRequestItem, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END
            FROM EventEquipmentRequestItem i JOIN i.eventEquipmentRequest r
            WHERE i.mainEquipment.mainEquipmentId = :equipmentId
              AND r.id <> :excludeRequestId
              AND r.status IN :statuses
              AND :effectiveStart < COALESCE(r.returnDatetime, r.endDatetime)
              AND :effectiveEnd   > COALESCE(r.pickupDatetime, r.startDatetime)
            """)
    boolean existsConflictingRequest(
            @Param("equipmentId") Long equipmentId,
            @Param("excludeRequestId") Long excludeRequestId,
            @Param("statuses") List<EventEquipmentRequestStatus> statuses,
            @Param("effectiveStart") LocalDateTime effectiveStart,
            @Param("effectiveEnd") LocalDateTime effectiveEnd);

    @Query("""
            SELECT DISTINCT i.mainEquipment.mainEquipmentId
            FROM EventEquipmentRequestItem i JOIN i.eventEquipmentRequest r
            WHERE i.mainEquipment.mainEquipmentId IN :ids
              AND r.status IN :statuses
              AND :now >= COALESCE(r.pickupDatetime, r.startDatetime)
              AND :now <= COALESCE(r.returnDatetime, r.endDatetime)
            """)
    List<Long> findBookedEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now,
            @Param("statuses") List<EventEquipmentRequestStatus> statuses);

    @Query("""
            SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.EventConflictRow(
                   i.mainEquipment.mainEquipmentId, r.id,
                   COALESCE(r.returnDatetime, r.endDatetime),
                   COALESCE(r.pickupDatetime, r.startDatetime))
            FROM EventEquipmentRequestItem i
            JOIN i.eventEquipmentRequest r
            WHERE i.mainEquipment.mainEquipmentId IN :equipmentIds
              AND r.status IN :statuses
              AND :startDate <= cast(COALESCE(r.returnDatetime, r.endDatetime) as date)
              AND :endDate   >= cast(COALESCE(r.pickupDatetime, r.startDatetime) as date)
            """)
    List<EventConflictRow> findEventConflictsForEquipment(
            @Param("equipmentIds") List<Long> equipmentIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") List<EventEquipmentRequestStatus> statuses);
}
