package com.ifoto.ifoto_backend.model;

import com.ifoto.ifoto_backend.model.enumerator.EquipmentCondition;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "main_equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MainEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "main_equipment_id")
    private Long mainEquipmentId;

    @Column(name = "equipment_type", nullable = false, length = 100)
    private String equipmentType;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    @Column(name = "serial_number", length = 100, unique = true)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "\"condition\"", length = 10)
    private EquipmentCondition condition;

    @Column(columnDefinition = "TEXT")
    private String problems;

    @Column(name = "lens_type", length = 50)
    private String lensType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_category_id")
    private RentalCategory pricingCategory;

    @Column(name = "is_for_rent", nullable = false)
    private boolean isForRent;

    @OneToMany(mappedBy = "mainEquipment", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<MainEquipmentStatus> dateStatuses = new ArrayList<>();
}
