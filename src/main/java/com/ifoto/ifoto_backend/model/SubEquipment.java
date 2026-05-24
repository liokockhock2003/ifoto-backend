package com.ifoto.ifoto_backend.model;

import com.ifoto.ifoto_backend.model.converter.StringListConverter;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sub_equipment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubEquipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sub_equipment_id")
    private Long subEquipmentId;

    @Column(name = "type", nullable = false, length = 100)
    private String type;

    @Column(name = "equipment_type", nullable = false, length = 100)
    private String equipmentType;

    @Builder.Default
    @Convert(converter = StringListConverter.class)
    @Column(name = "camera_model", columnDefinition = "JSON")
    private List<String> cameraModel = new ArrayList<>();

    @Column(length = 100)
    private String brand;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pricing_category_id")
    private RentalCategory pricingCategory;

    @Column(name = "is_for_rent")
    private boolean isForRent;

    @OneToMany(mappedBy = "subEquipment", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<SubEquipmentQuantityHold> quantityHolds = new ArrayList<>();
}
