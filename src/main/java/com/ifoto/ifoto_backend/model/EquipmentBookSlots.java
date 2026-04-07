package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "equipment_book_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentBookSlots {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "book_slot_id")
    private Long bookSlotId;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "main_equipment_id")
    private MainEquipment mainEquipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_equipment_id")
    private SubEquipment subEquipment;

    @Column(name = "quantity_used", nullable = false)
    private int quantityUsed;
}
