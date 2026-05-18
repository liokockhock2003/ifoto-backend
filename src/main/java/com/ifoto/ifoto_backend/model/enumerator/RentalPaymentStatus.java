package com.ifoto.ifoto_backend.model.enumerator;

public enum RentalPaymentStatus {
    NONE,
    ONLINE_PENDING,
    ONLINE_PAID,
    CASH_PENDING,
    CASH_PAID,
    PENALTY_PAID;

    public boolean isPaid() {
        return this == ONLINE_PAID || this == CASH_PAID || this == PENALTY_PAID;
    }
}
