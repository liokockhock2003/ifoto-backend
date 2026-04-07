-- ─────────────────────────────────────────────────────────────────
-- V8: Create main_equipment table
-- Stores individual (serialized) equipment items
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE main_equipment (
    main_equipment_id   BIGINT          AUTO_INCREMENT PRIMARY KEY,
    equipment_type      VARCHAR(100)    NOT NULL,
    brand               VARCHAR(100),
    model               VARCHAR(100),
    serial_number       VARCHAR(100)    UNIQUE,
    `condition`         VARCHAR(50),
    status              VARCHAR(50),
    notes               TEXT
);

CREATE INDEX idx_main_equipment_type   ON main_equipment(equipment_type);
CREATE INDEX idx_main_equipment_status ON main_equipment(status);
