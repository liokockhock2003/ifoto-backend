package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubBoundaryRow;
import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubQuantityRow;
import com.ifoto.ifoto_backend.model.SubEquipmentQuantityHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SubEquipmentQuantityHoldRepository extends JpaRepository<SubEquipmentQuantityHold, Long> {

    List<SubEquipmentQuantityHold> findBySubEquipmentSubEquipmentIdOrderByStartDatetimeAsc(Long subEquipmentId);

    @Query("SELECT h FROM SubEquipmentQuantityHold h WHERE h.subEquipment.subEquipmentId IN :ids ORDER BY h.startDatetime ASC")
    List<SubEquipmentQuantityHold> findAllBySubEquipmentIds(@Param("ids") List<Long> ids);

    @Query("SELECT h FROM SubEquipmentQuantityHold h WHERE h.subEquipment.subEquipmentId IN :ids AND h.endDatetime >= :now ORDER BY h.startDatetime ASC")
    List<SubEquipmentQuantityHold> findUpcomingBySubEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now);

    @Query("""
        SELECT COALESCE(SUM(h.quantity), 0) FROM SubEquipmentQuantityHold h
        WHERE h.subEquipment.subEquipmentId = :subEquipmentId
        AND :startDatetime < h.endDatetime AND :endDatetime > h.startDatetime
        AND h.id <> :excludeHoldId
    """)
    int sumHeldQuantity(
            @Param("subEquipmentId") Long subEquipmentId,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime,
            @Param("excludeHoldId") Long excludeHoldId);

    @Query("""
        SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.SubQuantityRow(
            h.subEquipment.subEquipmentId, COALESCE(SUM(h.quantity), 0))
        FROM SubEquipmentQuantityHold h
        WHERE :startDatetime < h.endDatetime AND :endDatetime > h.startDatetime
        GROUP BY h.subEquipment.subEquipmentId
    """)
    List<SubQuantityRow> sumHeldQuantityPerSubEquipment(
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime);

    @Query("""
        SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.SubBoundaryRow(
            h.subEquipment.subEquipmentId, h.id, h.endDatetime, SUM(h.quantity))
        FROM SubEquipmentQuantityHold h
        WHERE h.subEquipment.subEquipmentId IN :ids
        AND cast(h.endDatetime as date) = cast(:boundaryDate as date)
        GROUP BY h.subEquipment.subEquipmentId, h.id, h.endDatetime
    """)
    List<SubBoundaryRow> findBoundaryEndQuantities(
            @Param("ids") List<Long> ids,
            @Param("boundaryDate") LocalDateTime boundaryDate);

    @Query("""
        SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.SubBoundaryRow(
            h.subEquipment.subEquipmentId, h.id, h.startDatetime, SUM(h.quantity))
        FROM SubEquipmentQuantityHold h
        WHERE h.subEquipment.subEquipmentId IN :ids
        AND cast(h.startDatetime as date) = cast(:boundaryDate as date)
        GROUP BY h.subEquipment.subEquipmentId, h.id, h.startDatetime
    """)
    List<SubBoundaryRow> findBoundaryStartQuantities(
            @Param("ids") List<Long> ids,
            @Param("boundaryDate") LocalDateTime boundaryDate);
}
