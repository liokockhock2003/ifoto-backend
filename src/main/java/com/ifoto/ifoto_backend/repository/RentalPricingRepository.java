package com.ifoto.ifoto_backend.repository;

import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.model.RentalPricing;
import com.ifoto.ifoto_backend.model.RentalPricingCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RentalPricingRepository extends JpaRepository<RentalPricing, Long> {

    Optional<RentalPricing> findByPricingCategory_NameAndMemberType(
            RentalPricingCategory categoryName, MemberType memberType);

    List<RentalPricing> findByPricingCategory_Name(RentalPricingCategory categoryName);

    List<RentalPricing> findByMemberType(MemberType memberType);
}
