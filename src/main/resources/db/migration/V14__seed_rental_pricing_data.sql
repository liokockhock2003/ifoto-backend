-- ─────────────────────────────────────────────────────────────────
-- V14: Seed rental pricing data + link existing main_equipment rows
-- Source: Kadar Penyewaan Alatan rate card
-- ─────────────────────────────────────────────────────────────────

-- 1. Seed pricing categories
INSERT INTO rental_pricing_category (name) VALUES
('CAMERA'),
('SPEEDLIGHT'),
('LENS_NORMAL'),
('LENS_TELE');

-- 2. Seed rates (resolved via category name to avoid hard-coded IDs)
INSERT INTO rental_pricing (pricing_category_id, member_type, rate_1_day, rate_3_days, rate_per_day_extra, late_penalty_per_day)
-- Mahasiswa / Mahasiswi (STUDENT)
SELECT id,  'STUDENT',      55.00,  150.00, 25.00, 20.00 FROM rental_pricing_category WHERE name = 'CAMERA'      UNION ALL
SELECT id,  'STUDENT',      15.00,   30.00,  5.00, 20.00 FROM rental_pricing_category WHERE name = 'SPEEDLIGHT'  UNION ALL
SELECT id,  'STUDENT',      25.00,   60.00, 20.00, 20.00 FROM rental_pricing_category WHERE name = 'LENS_NORMAL' UNION ALL
SELECT id,  'STUDENT',      90.00,  225.00, 50.00, 20.00 FROM rental_pricing_category WHERE name = 'LENS_TELE'   UNION ALL
-- Bukan Mahasiswa / Mahasiswi (NON_STUDENT)
SELECT id,  'NON_STUDENT', 110.00,  350.00, 75.00, 40.00 FROM rental_pricing_category WHERE name = 'CAMERA'      UNION ALL
SELECT id,  'NON_STUDENT',  40.00,   90.00, 15.00, 40.00 FROM rental_pricing_category WHERE name = 'SPEEDLIGHT'  UNION ALL
SELECT id,  'NON_STUDENT',  40.00,   90.00, 30.00, 40.00 FROM rental_pricing_category WHERE name = 'LENS_NORMAL' UNION ALL
SELECT id,  'NON_STUDENT', 110.00,  360.00, 60.00, 40.00 FROM rental_pricing_category WHERE name = 'LENS_TELE';

-- 3. Back-fill pricing_category_id in main_equipment
--    Camera → CAMERA
UPDATE main_equipment me
JOIN   rental_pricing_category rpc ON rpc.name = 'CAMERA'
SET    me.pricing_category_id = rpc.id
WHERE  me.equipment_type = 'Camera';

--    Normal lenses → LENS_NORMAL
UPDATE main_equipment me
JOIN   rental_pricing_category rpc ON rpc.name = 'LENS_NORMAL'
SET    me.pricing_category_id = rpc.id
WHERE  me.equipment_type = 'Lens' AND me.lens_type = 'NORMAL';

--    Telephoto lenses → LENS_TELE
UPDATE main_equipment me
JOIN   rental_pricing_category rpc ON rpc.name = 'LENS_TELE'
SET    me.pricing_category_id = rpc.id
WHERE  me.equipment_type = 'Lens' AND me.lens_type = 'TELEPHOTO';

-- Note: PRIME lenses remain NULL (not included in rental pricing)
