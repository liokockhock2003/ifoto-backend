package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.RentalCategory;
import com.ifoto.ifoto_backend.model.RentalPricingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RentalCategoryRepository extends JpaRepository<RentalCategory, Long> {

    Optional<RentalCategory> findByName(RentalPricingCategory name);
}
