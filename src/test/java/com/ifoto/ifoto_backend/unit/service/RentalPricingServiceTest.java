package com.ifoto.ifoto_backend.unit.service;

import com.ifoto.ifoto_backend.model.RentalPricing;
import com.ifoto.ifoto_backend.repository.RentalPricingRepository;
import com.ifoto.ifoto_backend.service.RentalPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class RentalPricingServiceTest {

    @Mock
    private RentalPricingRepository pricingRepository;

    @InjectMocks
    private RentalPricingService service;

    private RentalPricing pricing;

    @BeforeEach
    void setUp() {
        pricing = new RentalPricing();
        pricing.setRate1Day(new BigDecimal("10.00"));
        pricing.setRate3Days(new BigDecimal("25.00"));
        pricing.setRatePerDayExtra(new BigDecimal("8.00"));
        pricing.setLatePenaltyPerDay(new BigDecimal("5.00"));
    }

    @Test
    void calculateCost_1Day_returnsRate1DayTimesOne() {
        assertEquals(new BigDecimal("10.00"), service.calculateCost(pricing, 1));
    }

    @Test
    void calculateCost_2Days_returnsRate1DayTimesTwo() {
        assertEquals(new BigDecimal("20.00"), service.calculateCost(pricing, 2));
    }

    @Test
    void calculateCost_3Days_returnsRate3Days() {
        assertEquals(new BigDecimal("25.00"), service.calculateCost(pricing, 3));
    }

    @Test
    void calculateCost_4Days_returnsRate3DaysPlusOneExtraDay() {
        // 25.00 + 8.00 * (4-3) = 33.00
        assertEquals(new BigDecimal("33.00"), service.calculateCost(pricing, 4));
    }

    @Test
    void calculateCost_7Days_returnsRate3DaysPlusFourExtraDays() {
        // 25.00 + 8.00 * (7-3) = 57.00
        assertEquals(new BigDecimal("57.00"), service.calculateCost(pricing, 7));
    }
}
