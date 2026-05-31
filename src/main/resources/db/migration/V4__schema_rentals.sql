-- ─────────────────────────────────────────────────────────────────────────────
-- V4: Rentals, payments, receipts, event equipment requests, and audit tables
-- Tables: equipment_rentals, equipment_rental_items, equipment_rental_sub_items,
--         payments, receipts,
--         event_equipment_requests, event_equipment_request_items,
--         event_equipment_request_sub_items,
--         main_equipment_statuses, sub_equipment_quantity_holds
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE equipment_rentals (
    id                   BIGINT   AUTO_INCREMENT PRIMARY KEY,
    rental_number        VARCHAR(20) UNIQUE,
    renter_id            BIGINT   NOT NULL,
    reviewed_by          BIGINT,
    status               ENUM('PENDING_REVIEW','APPROVED','REJECTED','CANCELLED',
                              'PENDING_PAYMENT','PAID','ACTIVE','OVERDUE','RETURNED')
                         NOT NULL DEFAULT 'PENDING_REVIEW',
    payment_method       ENUM('NONE','ONLINE','CASH') NOT NULL DEFAULT 'NONE',
    payment_status       ENUM('NONE','ONLINE_PENDING','ONLINE_PAID',
                              'CASH_PENDING','CASH_PAID','PENALTY_PAID')
                         NOT NULL DEFAULT 'NONE',
    requested_start_date DATE     NOT NULL,
    requested_end_date   DATE     NOT NULL,
    approved_start_date  DATE,
    approved_end_date    DATE,
    duration_days        INT,
    total_base_amount    BIGINT,
    total_penalty_amount BIGINT   NOT NULL DEFAULT 0,
    total_amount         BIGINT,
    rejection_reason     TEXT,
    committee_notes      TEXT,
    renter_notes         TEXT,
    approved_at          DATETIME,
    paid_at              DATETIME,
    active_at            DATETIME,
    returned_at          DATETIME,
    due_return_date      DATE,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT fk_er_renter      FOREIGN KEY (renter_id)   REFERENCES users(id),
    CONSTRAINT fk_er_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_er_renter_id ON equipment_rentals(renter_id);
CREATE INDEX idx_er_status    ON equipment_rentals(status);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE equipment_rental_items (
    id                   BIGINT   AUTO_INCREMENT PRIMARY KEY,
    equipment_rental_id  BIGINT   NOT NULL,
    main_equipment_id    BIGINT   NOT NULL,
    late_penalty_per_day BIGINT   NOT NULL,
    base_amount          BIGINT   NOT NULL,
    late_penalty_amount  BIGINT   NOT NULL DEFAULT 0,
    item_total_amount    BIGINT   NOT NULL,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT fk_eri_rental    FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id),
    CONSTRAINT fk_eri_equipment FOREIGN KEY (main_equipment_id)   REFERENCES main_equipment(main_equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_eri_rental_id    ON equipment_rental_items(equipment_rental_id);
CREATE INDEX idx_eri_equipment_id ON equipment_rental_items(main_equipment_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE equipment_rental_sub_items (
    id                   BIGINT   AUTO_INCREMENT PRIMARY KEY,
    equipment_rental_id  BIGINT   NOT NULL,
    sub_equipment_id     BIGINT   NOT NULL,
    borrowed_quantity    INT      NOT NULL,
    late_penalty_per_day BIGINT   NOT NULL DEFAULT 0,
    base_amount          BIGINT   NOT NULL DEFAULT 0,
    late_penalty_amount  BIGINT   NOT NULL DEFAULT 0,
    item_total_amount    BIGINT   NOT NULL DEFAULT 0,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT fk_ersi_rental        FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id) ON DELETE CASCADE,
    CONSTRAINT fk_ersi_sub_equipment FOREIGN KEY (sub_equipment_id)    REFERENCES sub_equipment(sub_equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ersi_rental_id        ON equipment_rental_sub_items(equipment_rental_id);
CREATE INDEX idx_ersi_sub_equipment_id ON equipment_rental_sub_items(sub_equipment_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE payments (
    id                  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    equipment_rental_id BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    bill_id             VARCHAR(50),
    collection_id       VARCHAR(50),
    amount              BIGINT        NOT NULL,
    paid_amount         BIGINT,
    payment_type        ENUM('ONLINE','CASH') NOT NULL,
    status              ENUM('PENDING','PAID','FAILED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    paid_at             DATETIME,
    due_at              DATE,
    payment_channel     VARCHAR(50),
    transaction_id      VARCHAR(100),
    transaction_status  VARCHAR(50),
    bill_url            VARCHAR(500),
    confirmed_by        BIGINT,
    confirmed_at        DATETIME,
    created_at          DATETIME      NOT NULL,
    updated_at          DATETIME      NOT NULL,
    CONSTRAINT fk_pay_rental       FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id),
    CONSTRAINT fk_pay_user         FOREIGN KEY (user_id)             REFERENCES users(id),
    CONSTRAINT fk_pay_confirmed_by FOREIGN KEY (confirmed_by)        REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pay_rental_id ON payments(equipment_rental_id);
CREATE INDEX idx_pay_bill_id   ON payments(bill_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE receipts (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY,
    receipt_number      VARCHAR(20)  NOT NULL UNIQUE,
    equipment_rental_id BIGINT       NOT NULL,
    payment_id          BIGINT       NULL,
    user_id             BIGINT       NOT NULL,
    document_type       ENUM('INVOICE','RECEIPT','OVERDUE_INVOICE','OVERDUE_RECEIPT')
                        NOT NULL DEFAULT 'RECEIPT',
    issued_at           DATETIME     NOT NULL,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    CONSTRAINT fk_rec_rental  FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id),
    CONSTRAINT fk_rec_payment FOREIGN KEY (payment_id)          REFERENCES payments(id),
    CONSTRAINT fk_rec_user    FOREIGN KEY (user_id)             REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rec_rental_id ON receipts(equipment_rental_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE event_equipment_requests (
    id                   BIGINT   AUTO_INCREMENT PRIMARY KEY,
    request_number       VARCHAR(20) UNIQUE,
    event_id             BIGINT   NOT NULL,
    requested_by         BIGINT   NOT NULL,
    reviewed_by          BIGINT,
    status               ENUM('PENDING_REVIEW','APPROVED','REJECTED','CANCELLED','ACTIVE','RETURNED')
                         NOT NULL,
    requested_start_date DATE     NOT NULL,
    requested_end_date   DATE     NOT NULL,
    approved_start_date  DATE,
    approved_end_date    DATE,
    duration_days        INT,
    rejection_reason     TEXT,
    committee_notes      TEXT,
    requester_notes      TEXT,
    approved_at          DATETIME,
    active_at            DATETIME,
    returned_at          DATETIME,
    due_return_date      DATE,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT fk_eer_event        FOREIGN KEY (event_id)     REFERENCES events(event_id) ON DELETE CASCADE,
    CONSTRAINT fk_eer_requested_by FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_eer_reviewed_by  FOREIGN KEY (reviewed_by)  REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_eer_event_id     ON event_equipment_requests(event_id);
CREATE INDEX idx_eer_requested_by ON event_equipment_requests(requested_by);
CREATE INDEX idx_eer_status       ON event_equipment_requests(status);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE event_equipment_request_items (
    id                         BIGINT   AUTO_INCREMENT PRIMARY KEY,
    event_equipment_request_id BIGINT   NOT NULL,
    main_equipment_id          BIGINT   NOT NULL,
    created_at                 DATETIME NOT NULL,
    updated_at                 DATETIME NOT NULL,
    CONSTRAINT fk_eeri_request   FOREIGN KEY (event_equipment_request_id) REFERENCES event_equipment_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_eeri_equipment FOREIGN KEY (main_equipment_id)          REFERENCES main_equipment(main_equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_eeri_request_id   ON event_equipment_request_items(event_equipment_request_id);
CREATE INDEX idx_eeri_equipment_id ON event_equipment_request_items(main_equipment_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE event_equipment_request_sub_items (
    id                         BIGINT   AUTO_INCREMENT PRIMARY KEY,
    event_equipment_request_id BIGINT   NOT NULL,
    sub_equipment_id           BIGINT   NOT NULL,
    borrowed_quantity          INT      NOT NULL,
    created_at                 DATETIME NOT NULL,
    updated_at                 DATETIME NOT NULL,
    CONSTRAINT fk_eer_sub_request   FOREIGN KEY (event_equipment_request_id) REFERENCES event_equipment_requests(id),
    CONSTRAINT fk_eer_sub_equipment FOREIGN KEY (sub_equipment_id)            REFERENCES sub_equipment(sub_equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_eer_sub_request_id   ON event_equipment_request_sub_items(event_equipment_request_id);
CREATE INDEX idx_eer_sub_equipment_id ON event_equipment_request_sub_items(sub_equipment_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE main_equipment_statuses (
    id                BIGINT      AUTO_INCREMENT PRIMARY KEY,
    main_equipment_id BIGINT      NOT NULL,
    status_type       VARCHAR(30) NOT NULL,
    start_date        DATE        NOT NULL,
    end_date          DATE        NOT NULL,
    notes             TEXT,
    created_at        DATETIME    NOT NULL,
    updated_at        DATETIME    NOT NULL,
    CONSTRAINT fk_mes_equipment FOREIGN KEY (main_equipment_id) REFERENCES main_equipment(main_equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_mes_equipment_id ON main_equipment_statuses(main_equipment_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE sub_equipment_quantity_holds (
    id               BIGINT   AUTO_INCREMENT PRIMARY KEY,
    sub_equipment_id BIGINT   NOT NULL,
    quantity         INT      NOT NULL,
    start_date       DATE     NOT NULL,
    end_date         DATE     NOT NULL,
    notes            TEXT,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    CONSTRAINT fk_seqh_equipment FOREIGN KEY (sub_equipment_id) REFERENCES sub_equipment(sub_equipment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_seqh_sub_equipment_id ON sub_equipment_quantity_holds(sub_equipment_id);
