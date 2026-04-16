package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "\"condition\"", length = 50)
    private String condition;

    @Column(length = 50)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "lens_type", length = 50)
    private String lensType;
}
