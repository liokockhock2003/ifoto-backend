package com.ifoto.ifoto_backend.service;

import com.ifoto.ifoto_backend.dto.RentalPricingDTO.RentalPricingBulkUpdateRequest;
import com.ifoto.ifoto_backend.dto.RentalPricingDTO.RentalPricingResponse;
import com.ifoto.ifoto_backend.model.MemberType;
import com.ifoto.ifoto_backend.model.RentalPricing;
import com.ifoto.ifoto_backend.model.RentalPricingCategory;
import com.ifoto.ifoto_backend.repository.RentalPricingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RentalPricingService {

    private final RentalPricingRepository pricingRepository;

    public List<RentalPricingResponse> getAll() {
        return pricingRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<RentalPricingResponse> bulkUpdate(RentalPricingBulkUpdateRequest request) {
        return request.items().stream().map(item -> {
            RentalPricing pricing = pricingRepository
                    .findByPricingCategory_NameAndMemberType(item.category(), item.memberType())
                    .orElseThrow(() -> new NoSuchElementException(
                            "No pricing found for category=%s memberType=%s".formatted(item.category(), item.memberType())));
            pricing.setRate1Day(item.rate1Day());
            pricing.setRate3Days(item.rate3Days());
            pricing.setRatePerDayExtra(item.ratePerDayExtra());
            pricing.setLatePenaltyPerDay(item.latePenaltyPerDay());
            return toResponse(pricingRepository.save(pricing));
        }).toList();
    }

    public BigDecimal calculateCost(RentalPricingCategory category, MemberType memberType, int durationDays) {
        RentalPricing pricing = pricingRepository
                .findByPricingCategory_NameAndMemberType(category, memberType)
                .orElseThrow(() -> new NoSuchElementException(
                        "No pricing found for category=%s memberType=%s".formatted(category, memberType)));
        if (durationDays == 1) return pricing.getRate1Day();
        if (durationDays == 3) return pricing.getRate3Days();
        return pricing.getRatePerDayExtra().multiply(BigDecimal.valueOf(durationDays));
    }

    private RentalPricingResponse toResponse(RentalPricing p) {
        return new RentalPricingResponse(
                p.getId(),
                p.getPricingCategory().getId(),
                p.getPricingCategory().getName(),
                p.getMemberType(),
                p.getRate1Day(),
                p.getRate3Days(),
                p.getRatePerDayExtra(),
                p.getLatePenaltyPerDay()
        );
    }
}
