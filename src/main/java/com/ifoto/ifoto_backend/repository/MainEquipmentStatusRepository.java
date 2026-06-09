package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.MainEquipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MainEquipmentStatusRepository extends JpaRepository<MainEquipmentStatus, Long> {

    List<MainEquipmentStatus> findByMainEquipmentMainEquipmentIdOrderByStartDatetimeAsc(Long mainEquipmentId);

    @Query("SELECT s FROM MainEquipmentStatus s WHERE s.mainEquipment.mainEquipmentId IN :ids ORDER BY s.startDatetime ASC")
    List<MainEquipmentStatus> findAllByEquipmentIds(@Param("ids") List<Long> ids);

    @Query("SELECT s FROM MainEquipmentStatus s WHERE s.mainEquipment.mainEquipmentId IN :ids AND s.endDatetime >= :now ORDER BY s.startDatetime ASC")
    List<MainEquipmentStatus> findUpcomingByEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now);

    @Query("SELECT s FROM MainEquipmentStatus s WHERE s.mainEquipment.mainEquipmentId IN :ids AND s.startDatetime <= :now AND s.endDatetime >= :now")
    List<MainEquipmentStatus> findActiveByEquipmentIds(
            @Param("ids") List<Long> ids,
            @Param("now") LocalDateTime now);

    @Query("""
        SELECT COUNT(s) > 0 FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId = :equipmentId
        AND cast(s.startDatetime as date) < :endDate
        AND cast(s.endDatetime   as date) > :startDate
    """)
    boolean existsInteriorConflictingStatus(
            @Param("equipmentId") Long equipmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT COUNT(s) > 0 FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId = :equipmentId
        AND :startDatetime < s.endDatetime AND :endDatetime > s.startDatetime
    """)
    boolean existsConflictingStatus(
            @Param("equipmentId") Long equipmentId,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime);

    @Query("""
        SELECT COUNT(s) > 0 FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId = :equipmentId
        AND s.id <> :excludeId
        AND :startDatetime < s.endDatetime AND :endDatetime > s.startDatetime
    """)
    boolean existsConflictingStatusExcluding(
            @Param("equipmentId") Long equipmentId,
            @Param("excludeId") Long excludeId,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime);

    @Query("""
        SELECT s.mainEquipment.mainEquipmentId, s.statusType
        FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId IN :ids
        AND :startDatetime < s.endDatetime AND :endDatetime > s.startDatetime
    """)
    List<Object[]> findStatusTypesForRange(
            @Param("ids") List<Long> ids,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime);

    @Query("""
        SELECT s.mainEquipment.mainEquipmentId, s.id, s.startDatetime, s.endDatetime
        FROM MainEquipmentStatus s
        WHERE s.mainEquipment.mainEquipmentId IN :ids
        AND :startDatetime < s.endDatetime AND :endDatetime > s.startDatetime
    """)
    List<Object[]> findStatusWindowConflictsForEquipment(
            @Param("ids") List<Long> ids,
            @Param("startDatetime") LocalDateTime startDatetime,
            @Param("endDatetime") LocalDateTime endDatetime);
}
