CREATE TABLE equipment_rental_items (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_rental_id   BIGINT NOT NULL,
    main_equipment_id     BIGINT NOT NULL,
    member_type           ENUM('STUDENT','NON_STUDENT') NOT NULL,
    pricing_category      VARCHAR(20) NOT NULL,
    rate_1_day            BIGINT NOT NULL,
    rate_3_days           BIGINT NOT NULL,
    rate_per_day_extra    BIGINT NOT NULL,
    late_penalty_per_day  BIGINT NOT NULL,
    base_amount           BIGINT NOT NULL,
    late_penalty_amount   BIGINT NOT NULL DEFAULT 0,
    item_total_amount     BIGINT NOT NULL,
    created_at            DATETIME NOT NULL,
    updated_at            DATETIME NOT NULL,
    FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id),
    FOREIGN KEY (main_equipment_id)   REFERENCES main_equipment(main_equipment_id)
);
