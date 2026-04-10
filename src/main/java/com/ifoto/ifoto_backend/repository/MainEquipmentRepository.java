package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.MainEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MainEquipmentRepository extends JpaRepository<MainEquipment, Long> {

    @Modifying
    @Query("DELETE FROM MainEquipment e WHERE e.mainEquipmentId = :id")
    int deleteByIdReturningCount(@Param("id") Long id);
}
