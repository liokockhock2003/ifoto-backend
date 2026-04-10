package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.SubEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SubEquipmentRepository extends JpaRepository<SubEquipment, Long> {

    @Modifying
    @Query("DELETE FROM SubEquipment e WHERE e.subEquipmentId = :id")
    int deleteByIdReturningCount(Long id);
}
