-- Migrate any PENDING_CASH rows to PENDING_PAYMENT before removing from ENUM
UPDATE equipment_rentals SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING_CASH';

ALTER TABLE equipment_rentals
    MODIFY COLUMN status ENUM('PENDING_REVIEW','APPROVED','REJECTED','CANCELLED',
        'PENDING_PAYMENT','PAID','ACTIVE','OVERDUE','RETURNED') NOT NULL DEFAULT 'PENDING_REVIEW';

-- Migrate CASH_CONFIRMED rows to CASH_PAID before removing from ENUM
UPDATE equipment_rentals SET payment_status = 'CASH_PAID' WHERE payment_status = 'CASH_CONFIRMED';

ALTER TABLE equipment_rentals
    MODIFY COLUMN payment_status ENUM('NONE','ONLINE_PENDING','ONLINE_PAID',
        'CASH_PENDING','CASH_PAID','PENALTY_PAID') NOT NULL DEFAULT 'NONE';
