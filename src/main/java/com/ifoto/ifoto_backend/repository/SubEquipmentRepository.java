package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.SubEquipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubEquipmentRepository extends JpaRepository<SubEquipment, Long> {

    List<SubEquipment> findByIsForRentTrue();

    @Modifying
    @Query("DELETE FROM SubEquipment e WHERE e.subEquipmentId = :id")
    int deleteByIdReturningCount(@Param("id") Long id);
}
