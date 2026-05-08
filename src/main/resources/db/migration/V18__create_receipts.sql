CREATE TABLE receipts (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    receipt_number        VARCHAR(20) UNIQUE NOT NULL,
    equipment_rental_id   BIGINT NOT NULL UNIQUE,
    payment_id            BIGINT NOT NULL UNIQUE,
    user_id               BIGINT NOT NULL,
    issued_at             DATETIME NOT NULL,
    created_at            DATETIME NOT NULL,
    updated_at            DATETIME NOT NULL,
    FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id),
    FOREIGN KEY (payment_id)          REFERENCES payments(id),
    FOREIGN KEY (user_id)             REFERENCES users(id)
);
