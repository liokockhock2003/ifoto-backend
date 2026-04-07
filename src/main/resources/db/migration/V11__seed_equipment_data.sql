-- V11: Seed equipment test data
-- Populates main_equipment, sub_equipment, and equipment_book_slots for dev/testing

-- ── main_equipment ────────────────────────────────────────────────────────────
INSERT INTO main_equipment (equipment_type, brand, model, serial_number, `condition`, status, notes) VALUES
('Camera Body',  'Sony',   'A7 IV',         'SN-SONY-A7IV-001',   'Good',      'Available', 'Primary mirrorless body'),
('Camera Body',  'Sony',   'A7 III',        'SN-SONY-A7III-001',  'Good',      'Available', 'Backup mirrorless body'),
('Camera Body',  'Canon',  'EOS R5',        'SN-CANON-R5-001',    'Excellent', 'Available', 'High-res event body'),
('Tripod',       'Manfrotto', '190XPRO3',   'SN-MF-190X-001',     'Good',      'Available', 'Aluminium 3-section tripod'),
('Tripod',       'Joby',   'GorillaPod 5K', 'SN-JOBY-GP5K-001',   'Fair',      'Available', 'Flexible mini tripod'),
('Flash',        'Godox',  'V1-S',          'SN-GODOX-V1S-001',   'Excellent', 'Available', 'Round-head flash for Sony'),
('Flash',        'Godox',  'V1-C',          'SN-GODOX-V1C-001',   'Good',      'In Use',    'Round-head flash for Canon'),
('Drone',        'DJI',    'Mini 3 Pro',    'SN-DJI-M3P-001',     'Excellent', 'Available', 'Lightweight foldable drone'),
('Gimbal',       'DJI',    'RS 3',          'SN-DJI-RS3-001',     'Good',      'Available', '3-axis camera stabiliser'),
('Light Stand',  'Neewer', 'Air Cushioned', 'SN-NW-ACS-001',      'Fair',      'Available', '200cm air-cushioned stand');

-- ── sub_equipment ─────────────────────────────────────────────────────────────
INSERT INTO sub_equipment (equipment_type, brand, model, capacity, total_quantity, used_quantity, available_quantity, notes) VALUES
('Lens',         'Sony',   'FE 24-70mm f/2.8 GM',  1, 3, 1, 2, 'Standard zoom for Sony E-mount'),
('Lens',         'Sony',   'FE 70-200mm f/2.8 GM',  1, 2, 0, 2, 'Telephoto zoom for Sony E-mount'),
('Lens',         'Canon',  'RF 24-70mm f/2.8L IS',  1, 2, 1, 1, 'Standard zoom for Canon RF-mount'),
('Lens',         'Sony',   'FE 16-35mm f/2.8 GM',   1, 2, 0, 2, 'Wide-angle zoom for Sony E-mount'),
('Memory Card',  'Sony',   'CFexpress Type A 160GB', 4, 10, 3, 7, 'High-speed cards for A7 IV / A7 III'),
('Memory Card',  'Lexar',  'SDXC UHS-II 128GB',      4, 8,  2, 6, 'UHS-II cards for Canon R5'),
('Battery',      'Sony',   'NP-FZ100',               1, 12, 4, 8, 'Batteries for Sony A7 series'),
('Battery',      'Canon',  'LP-E6NH',                1, 8,  2, 6, 'Batteries for Canon EOS R series'),
('ND Filter',    'K&F',    'Variable ND2-ND400 77mm', 1, 5, 1, 4, 'Variable ND for video use'),
('Backdrop',     'Neewer', 'Muslin 3x6m White',      1, 3, 0, 3, 'White backdrop for studio shoots');

-- ── equipment_book_slots ──────────────────────────────────────────────────────
-- Slot 1: Sony A7 IV booked for a weekend shoot
INSERT INTO equipment_book_slots (start_date, end_date, main_equipment_id, sub_equipment_id, quantity_used)
SELECT '2026-04-10 08:00:00', '2026-04-11 20:00:00', m.main_equipment_id, NULL, 1
FROM main_equipment m WHERE m.serial_number = 'SN-SONY-A7IV-001';

-- Slot 2: Canon R5 booked with a Canon RF lens
INSERT INTO equipment_book_slots (start_date, end_date, main_equipment_id, sub_equipment_id, quantity_used)
SELECT '2026-04-12 09:00:00', '2026-04-12 18:00:00', m.main_equipment_id, NULL, 1
FROM main_equipment m WHERE m.serial_number = 'SN-CANON-R5-001';

-- Slot 3: Sony FE 24-70mm GM lens pool booking (1 unit)
INSERT INTO equipment_book_slots (start_date, end_date, main_equipment_id, sub_equipment_id, quantity_used)
SELECT '2026-04-10 08:00:00', '2026-04-11 20:00:00', NULL, s.sub_equipment_id, 1
FROM sub_equipment s WHERE s.model = 'FE 24-70mm f/2.8 GM';

-- Slot 4: Canon RF 24-70mm lens pool booking (1 unit)
INSERT INTO equipment_book_slots (start_date, end_date, main_equipment_id, sub_equipment_id, quantity_used)
SELECT '2026-04-12 09:00:00', '2026-04-12 18:00:00', NULL, s.sub_equipment_id, 1
FROM sub_equipment s WHERE s.model = 'RF 24-70mm f/2.8L IS';

-- Slot 5: Godox V1-C flash (currently in use)
INSERT INTO equipment_book_slots (start_date, end_date, main_equipment_id, sub_equipment_id, quantity_used)
SELECT '2026-04-05 10:00:00', '2026-04-07 18:00:00', m.main_equipment_id, NULL, 1
FROM main_equipment m WHERE m.serial_number = 'SN-GODOX-V1C-001';
