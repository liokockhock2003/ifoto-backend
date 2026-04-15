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

    @Column(name = "used_quantity", nullable = false)
    private int usedQuantity;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "subEquipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EquipmentBookSlots> bookSlots = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void validateQuantities() {
        if (usedQuantity > totalQuantity) {
            throw new IllegalStateException(
                    "usedQuantity (" + usedQuantity + ") cannot exceed totalQuantity (" + totalQuantity + ")");
        }
        if (usedQuantity + availableQuantity != totalQuantity) {
            throw new IllegalStateException(
                    "usedQuantity + availableQuantity must equal totalQuantity");
        }
    }
}
