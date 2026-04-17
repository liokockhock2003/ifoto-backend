-- ─────────────────────────────────────────────────────────────────
-- V13: Create rental_pricing_category and rental_pricing tables,
--      then add FK from main_equipment now that the category table exists
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE rental_pricing_category (
    id   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    CONSTRAINT uq_rental_category_name UNIQUE (name)
);

CREATE TABLE rental_pricing (
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY,
    pricing_category_id  BIGINT       NOT NULL,
    member_type          VARCHAR(15)  NOT NULL,   -- STUDENT | NON_STUDENT
    rate_1_day           DECIMAL(8,2) NOT NULL,
    rate_3_days          DECIMAL(8,2) NOT NULL,
    rate_per_day_extra   DECIMAL(8,2) NOT NULL,   -- per-day rate when duration > 3 days
    late_penalty_per_day DECIMAL(8,2) NOT NULL,
    CONSTRAINT uq_pricing UNIQUE (pricing_category_id, member_type),
    CONSTRAINT fk_rp_pricing_category
        FOREIGN KEY (pricing_category_id) REFERENCES rental_pricing_category (id)
);

-- Add FK from main_equipment now that rental_pricing_category exists
ALTER TABLE main_equipment
    ADD CONSTRAINT fk_me_pricing_category
        FOREIGN KEY (pricing_category_id) REFERENCES rental_pricing_category (id);
