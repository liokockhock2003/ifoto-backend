package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.MainEquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MainEquipmentStatusRepository extends JpaRepository<MainEquipmentStatus, Long> {

    List<MainEquipmentStatus> findByMainEquipmentMainEquipmentIdOrderByStartDateAsc(Long mainEquipmentId);

    @Query("SELECT s FROM MainEquipmentStatus s WHERE s.mainEquipment.mainEquipmentId IN :ids AND s.endDate >= :today ORDER BY s.startDate ASC")
    List<MainEquipmentStatus> findUpcomingByEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("today") LocalDate today);

    @Query("SELECT s FROM MainEquipmentStatus s WHERE s.mainEquipment.mainEquipmentId IN :ids AND s.startDate <= :today AND s.endDate >= :today")
    List<MainEquipmentStatus> findActiveByEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("today") LocalDate today);

    @Query("""
        SELECT COUNT(s) > 0 FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId = :equipmentId
        AND :startDate <= s.endDate AND :endDate >= s.startDate
    """)
    boolean existsConflictingStatus(
            @Param("equipmentId") Long equipmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT COUNT(s) > 0 FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId = :equipmentId
        AND s.id <> :excludeId
        AND :startDate <= s.endDate AND :endDate >= s.startDate
    """)
    boolean existsConflictingStatusExcluding(
            @Param("equipmentId") Long equipmentId,
            @Param("excludeId") Long excludeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
