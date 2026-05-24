package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentMethod;
import com.ifoto.ifoto_backend.model.enumerator.RentalPaymentStatus;
import com.ifoto.ifoto_backend.model.enumerator.RentalStatus;

@Entity
@Table(name = "equipment_rentals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentRental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rental_number", length = 20, unique = true)
    private String rentalNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "renter_id", nullable = false)
    private User renter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RentalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    @Builder.Default
    private RentalPaymentMethod paymentMethod = RentalPaymentMethod.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    @Builder.Default
    private RentalPaymentStatus paymentStatus = RentalPaymentStatus.NONE;

    @Column(name = "requested_start_date", nullable = false)
    private LocalDate requestedStartDate;

    @Column(name = "requested_end_date", nullable = false)
    private LocalDate requestedEndDate;

    @Column(name = "approved_start_date")
    private LocalDate approvedStartDate;

    @Column(name = "approved_end_date")
    private LocalDate approvedEndDate;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "total_base_amount")
    private Long totalBaseAmount;

    @Column(name = "total_penalty_amount")
    @Builder.Default
    private Long totalPenaltyAmount = 0L;

    @Column(name = "total_amount")
    private Long totalAmount;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "committee_notes", columnDefinition = "TEXT")
    private String committeeNotes;

    @Column(name = "renter_notes", columnDefinition = "TEXT")
    private String renterNotes;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "active_at")
    private LocalDateTime activeAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(name = "due_return_date")
    private LocalDate dueReturnDate;

    @OneToMany(mappedBy = "equipmentRental", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<EquipmentRentalItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "equipmentRental", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<EquipmentRentalSubItem> subItems = new ArrayList<>();

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
