ALTER TABLE sub_equipment
    ADD COLUMN pricing_category_id BIGINT NULL,
    ADD COLUMN is_for_rent         TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE sub_equipment
    ADD CONSTRAINT fk_sub_equipment_pricing_category
    FOREIGN KEY (pricing_category_id) REFERENCES rental_pricing_category(id);
