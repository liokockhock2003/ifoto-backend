-- ─────────────────────────────────────────────────────────────────
-- V10: Create equipment_book_slots table
-- Links a booking time window to a main or sub equipment item
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE equipment_book_slots (
    book_slot_id        BIGINT      AUTO_INCREMENT PRIMARY KEY,
    start_date          DATETIME    NOT NULL,
    end_date            DATETIME    NOT NULL,
    main_equipment_id   BIGINT,
    sub_equipment_id    BIGINT,
    quantity_used       INT         NOT NULL,

    CONSTRAINT fk_book_slot_main_equipment
        FOREIGN KEY (main_equipment_id)
        REFERENCES main_equipment(main_equipment_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_book_slot_sub_equipment
        FOREIGN KEY (sub_equipment_id)
        REFERENCES sub_equipment(sub_equipment_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_book_slots_main_equipment ON equipment_book_slots(main_equipment_id);
CREATE INDEX idx_book_slots_sub_equipment  ON equipment_book_slots(sub_equipment_id);
CREATE INDEX idx_book_slots_dates          ON equipment_book_slots(start_date, end_date);
