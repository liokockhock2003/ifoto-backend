package com.ifoto.ifoto_backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ifoto.ifoto_backend.model.enumerator.PaymentRecordStatus;
import com.ifoto.ifoto_backend.model.enumerator.PaymentType;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_rental_id", nullable = false)
    private EquipmentRental equipmentRental;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "bill_id", length = 50)
    private String billId;

    @Column(name = "collection_id", length = 50)
    private String collectionId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "paid_amount")
    private Long paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentRecordStatus status = PaymentRecordStatus.PENDING;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "due_at")
    private LocalDate dueAt;

    @Column(name = "payment_channel", length = 50)
    private String paymentChannel;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "transaction_status", length = 50)
    private String transactionStatus;

    @Column(name = "bill_url", length = 500)
    private String billUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

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
