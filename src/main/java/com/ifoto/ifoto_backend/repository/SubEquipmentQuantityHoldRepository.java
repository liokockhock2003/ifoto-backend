package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.SubEquipmentQuantityHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubEquipmentQuantityHoldRepository extends JpaRepository<SubEquipmentQuantityHold, Long> {

    List<SubEquipmentQuantityHold> findBySubEquipmentSubEquipmentIdOrderByStartDateAsc(Long subEquipmentId);

    @Query("SELECT h FROM SubEquipmentQuantityHold h WHERE h.subEquipment.subEquipmentId IN :ids AND h.endDate >= :today ORDER BY h.startDate ASC")
    List<SubEquipmentQuantityHold> findUpcomingBySubEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("today") LocalDate today);

    @Query("""
        SELECT COALESCE(SUM(h.quantity), 0) FROM SubEquipmentQuantityHold h
        WHERE h.subEquipment.subEquipmentId = :subEquipmentId
        AND :startDate <= h.endDate AND :endDate >= h.startDate
        AND h.id <> :excludeHoldId
    """)
    int sumHeldQuantity(
            @Param("subEquipmentId") Long subEquipmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludeHoldId") Long excludeHoldId);

    @Query("""
        SELECT h.subEquipment.subEquipmentId, COALESCE(SUM(h.quantity), 0)
        FROM SubEquipmentQuantityHold h
        WHERE :startDate <= h.endDate AND :endDate >= h.startDate
        GROUP BY h.subEquipment.subEquipmentId
    """)
    List<Object[]> sumHeldQuantityPerSubEquipment(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
