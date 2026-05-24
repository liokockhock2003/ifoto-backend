package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_rental_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentRentalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_rental_id", nullable = false)
    private EquipmentRental equipmentRental;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_equipment_id", nullable = false)
    private MainEquipment mainEquipment;

    @Column(name = "late_penalty_per_day", nullable = false)
    private Long latePenaltyPerDay;

    @Column(name = "base_amount", nullable = false)
    private Long baseAmount;

    @Column(name = "late_penalty_amount", nullable = false)
    @Builder.Default
    private Long latePenaltyAmount = 0L;

    @Column(name = "item_total_amount", nullable = false)
    private Long itemTotalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
