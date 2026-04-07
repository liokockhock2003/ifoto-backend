-- ─────────────────────────────────────────────────────────────────
-- V9: Create sub_equipment table
-- Stores pooled/quantity-tracked equipment (e.g. lenses, cables)
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE sub_equipment (
    sub_equipment_id    BIGINT          AUTO_INCREMENT PRIMARY KEY,
    equipment_type      VARCHAR(100)    NOT NULL,
    brand               VARCHAR(100),
    model               VARCHAR(100),
    capacity            INT             NOT NULL,
    total_quantity      INT             NOT NULL,
    used_quantity       INT             NOT NULL DEFAULT 0,
    available_quantity  INT             NOT NULL,
    notes               TEXT
);

CREATE INDEX idx_sub_equipment_type ON sub_equipment(equipment_type);
