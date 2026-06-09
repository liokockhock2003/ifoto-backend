package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_equipment_request_sub_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventEquipmentRequestSubItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_equipment_request_id", nullable = false)
    private EventEquipmentRequest eventEquipmentRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_equipment_id", nullable = false)
    private SubEquipment subEquipment;

    @Column(name = "borrowed_quantity", nullable = false)
    private int borrowedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
