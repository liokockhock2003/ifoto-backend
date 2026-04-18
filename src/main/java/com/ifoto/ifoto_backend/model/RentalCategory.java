package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rental_pricing_category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RentalCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private RentalPricingCategory name;
}
