package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.SubEquipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubEquipmentRepository extends JpaRepository<SubEquipment, Long> {

    List<SubEquipment> findByIsForRentTrue();
}
