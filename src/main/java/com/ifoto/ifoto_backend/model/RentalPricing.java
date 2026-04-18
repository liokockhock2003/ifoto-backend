package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
        name = "rental_pricing",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pricing",
                columnNames = {"pricing_category_id", "member_type"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_category_id", nullable = false)
    private RentalCategory pricingCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 15)
    private MemberType memberType;

    @Column(name = "rate_1_day", nullable = false, precision = 8, scale = 2)
    private BigDecimal rate1Day;

    @Column(name = "rate_3_days", nullable = false, precision = 8, scale = 2)
    private BigDecimal rate3Days;

    @Column(name = "rate_per_day_extra", nullable = false, precision = 8, scale = 2)
    private BigDecimal ratePerDayExtra;

    @Column(name = "late_penalty_per_day", nullable = false, precision = 8, scale = 2)
    private BigDecimal latePenaltyPerDay;
}
