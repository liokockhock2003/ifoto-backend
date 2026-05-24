package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentBookedRange;
import com.ifoto.ifoto_backend.model.EquipmentRentalSubItem;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface EquipmentRentalSubItemRepository extends JpaRepository<EquipmentRentalSubItem, Long> {

    List<EquipmentRentalSubItem> findByEquipmentRentalId(Long rentalId);

    @Query("""
            SELECT new com.ifoto.ifoto_backend.dto.EquipmentDTO.SubEquipmentBookedRange(
                ersi.subEquipment.subEquipmentId,
                COALESCE(er.approvedStartDate, er.requestedStartDate),
                COALESCE(er.approvedEndDate, er.requestedEndDate),
                ersi.borrowedQuantity,
                CASE WHEN er.status = :pendingStatus THEN TRUE ELSE FALSE END
            )
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE ersi.subEquipment.subEquipmentId IN :subEquipmentIds
            AND er.status IN :statuses
            """)
    List<SubEquipmentBookedRange> findBookedQuantityRanges(
            @Param("subEquipmentIds") List<Long> subEquipmentIds,
            @Param("statuses") Collection<RentalStatus> statuses,
            @Param("pendingStatus") RentalStatus pendingStatus
    );

    @Query("""
            SELECT COALESCE(SUM(ersi.borrowedQuantity), 0)
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE ersi.subEquipment.subEquipmentId = :subEquipmentId
            AND er.status IN :statuses
            AND :startDate <= COALESCE(er.approvedEndDate, er.requestedEndDate)
            AND :endDate   >= COALESCE(er.approvedStartDate, er.requestedStartDate)
            """)
    int sumCommittedQuantity(
            @Param("subEquipmentId") Long subEquipmentId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<RentalStatus> statuses
    );

    @Query("""
            SELECT ersi.subEquipment.subEquipmentId, COALESCE(SUM(ersi.borrowedQuantity), 0)
            FROM EquipmentRentalSubItem ersi JOIN ersi.equipmentRental er
            WHERE er.status IN :statuses
            AND :startDate <= COALESCE(er.approvedEndDate, er.requestedEndDate)
            AND :endDate   >= COALESCE(er.approvedStartDate, er.requestedStartDate)
            GROUP BY ersi.subEquipment.subEquipmentId
            """)
    List<Object[]> sumCommittedQuantityPerSubEquipment(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<RentalStatus> statuses
    );
}
