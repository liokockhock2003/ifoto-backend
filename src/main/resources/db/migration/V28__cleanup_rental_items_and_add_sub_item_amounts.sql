-- Remove 5 write-only snapshot columns from equipment_rental_items
ALTER TABLE equipment_rental_items
    DROP COLUMN member_type,
    DROP COLUMN pricing_category,
    DROP COLUMN rate_1_day,
    DROP COLUMN rate_3_days,
    DROP COLUMN rate_per_day_extra;

-- Add financial tracking columns to equipment_rental_sub_items for consistency
ALTER TABLE equipment_rental_sub_items
    ADD COLUMN late_penalty_per_day BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN base_amount          BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN late_penalty_amount  BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN item_total_amount    BIGINT NOT NULL DEFAULT 0;
