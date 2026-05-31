-- ─────────────────────────────────────────────────────────────────────────────
-- V2: Equipment schema
-- Tables: rental_pricing_category, rental_pricing, main_equipment, sub_equipment
-- rental_pricing_category is created first because both equipment tables FK to it.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE rental_pricing_category (
    id   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    CONSTRAINT uq_rental_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rental_pricing (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    pricing_category_id  BIGINT       NOT NULL,
    member_type          VARCHAR(15)  NOT NULL,
    rate_1_day           DECIMAL(8,2) NOT NULL,
    rate_3_days          DECIMAL(8,2) NOT NULL,
    rate_per_day_extra   DECIMAL(8,2) NOT NULL,
    late_penalty_per_day DECIMAL(8,2) NOT NULL,
    CONSTRAINT uq_pricing UNIQUE (pricing_category_id, member_type),
    CONSTRAINT fk_rp_pricing_category FOREIGN KEY (pricing_category_id) REFERENCES rental_pricing_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE main_equipment (
    main_equipment_id   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    equipment_type      VARCHAR(100) NOT NULL,
    brand               VARCHAR(100),
    lens_type           VARCHAR(50),
    model               VARCHAR(100),
    serial_number       VARCHAR(100) UNIQUE,
    `condition`         VARCHAR(50),
    status              VARCHAR(50),
    notes               TEXT,
    pricing_category_id BIGINT       NULL,
    is_for_rent         TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT fk_me_pricing_category FOREIGN KEY (pricing_category_id) REFERENCES rental_pricing_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_main_equipment_type   ON main_equipment(equipment_type);
CREATE INDEX idx_main_equipment_status ON main_equipment(status);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE sub_equipment (
    sub_equipment_id    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    type                VARCHAR(100) NOT NULL,
    equipment_type      VARCHAR(100) NOT NULL,
    camera_model        JSON,
    brand               VARCHAR(100),
    capacity            INT          NOT NULL,
    total_quantity      INT          NOT NULL,
    notes               TEXT,
    pricing_category_id BIGINT       NULL,
    is_for_rent         TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT fk_sub_equipment_pricing_category FOREIGN KEY (pricing_category_id) REFERENCES rental_pricing_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_sub_equipment_type ON sub_equipment(equipment_type);
