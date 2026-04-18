-- ─────────────────────────────────────────────────────────────────
-- V7: Create main_equipment table
-- Stores individual (serialized) equipment items
-- pricing_category_id FK is added in V13 after rental_pricing_category exists
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE main_equipment (
    main_equipment_id   BIGINT          AUTO_INCREMENT PRIMARY KEY,
    equipment_type      VARCHAR(100)    NOT NULL,
    brand               VARCHAR(100),
    lens_type           VARCHAR(50),
    model               VARCHAR(100),
    serial_number       VARCHAR(100)    UNIQUE,
    `condition`         VARCHAR(50),
    status              VARCHAR(50),
    notes               TEXT,
    pricing_category_id BIGINT,
    is_for_rent         TINYINT(1) NOT NULL DEFAULT 0
);

CREATE INDEX idx_main_equipment_type   ON main_equipment(equipment_type);
CREATE INDEX idx_main_equipment_status ON main_equipment(status);
