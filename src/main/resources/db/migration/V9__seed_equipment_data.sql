-- V9: Seed equipment test data
-- equipment_type: Camera | Lens   brands: Canon | Nikon
-- lens_type (Lens only): PRIME | NORMAL | TELEPHOTO

-- ── main_equipment ────────────────────────────────────────────────────────────
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, status, notes, is_for_rent) VALUES
-- Cameras – Canon
('Camera', 'Canon', NULL,        'EOS R5',                 'SN-CANON-R5-001',        'Excellent', 'Available', 'High-res full-frame mirrorless',      1),
('Camera', 'Canon', NULL,        'EOS R6 II',              'SN-CANON-R6II-001',      'Good',      'Available', 'Sports & event mirrorless body',       1),
-- Cameras – Nikon
('Camera', 'Nikon', NULL,        'Z8',                     'SN-NIKON-Z8-001',        'Excellent', 'Available', 'High-res Z-mount mirrorless',          1),
('Camera', 'Nikon', NULL,        'Z6 III',                 'SN-NIKON-Z6III-001',     'Good',      'Available', 'Hybrid photo/video mirrorless body',   1),
-- Lenses – Canon – PRIME (not for rent)
('Lens',   'Canon', 'PRIME',     'RF 50mm f/1.2L',         'SN-CANON-L-50-001',      'Excellent', 'Available', 'Flagship Canon prime lens',            0),
('Lens',   'Canon', 'PRIME',     'RF 85mm f/1.2L DS',      'SN-CANON-L-85-001',      'Good',      'Available', 'Portrait prime with DS coating',       0),
-- Lenses – Canon – NORMAL
('Lens',   'Canon', 'NORMAL',    'RF 24-70mm f/2.8L',      'SN-CANON-L-2470-001',    'Excellent', 'Available', 'Standard zoom for Canon RF-mount',     1),
('Lens',   'Canon', 'NORMAL',    'RF 15-35mm f/2.8L',      'SN-CANON-L-1535-001',    'Good',      'Available', 'Wide-angle zoom for Canon RF',         1),
-- Lenses – Canon – TELEPHOTO
('Lens',   'Canon', 'TELEPHOTO', 'RF 70-200mm f/2.8L',     'SN-CANON-L-70200-001',   'Excellent', 'Available', 'Telephoto zoom for Canon RF',          1),
('Lens',   'Canon', 'TELEPHOTO', 'RF 100-500mm f/4.5L',    'SN-CANON-L-100500-001',  'Good',      'Available', 'Super-telephoto zoom for Canon RF',    1),
-- Lenses – Nikon – PRIME (not for rent)
('Lens',   'Nikon', 'PRIME',     'NIKKOR Z 50mm f/1.2',    'SN-NIKON-L-50-001',      'Excellent', 'Available', 'Flagship Nikon Z prime lens',          0),
('Lens',   'Nikon', 'PRIME',     'NIKKOR Z 85mm f/1.2',    'SN-NIKON-L-85-001',      'Good',      'Available', 'Portrait prime for Z-mount',           0),
-- Lenses – Nikon – NORMAL
('Lens',   'Nikon', 'NORMAL',    'NIKKOR Z 24-70mm f/2.8', 'SN-NIKON-L-2470-001',    'Excellent', 'Available', 'Standard zoom for Z-mount',            1),
('Lens',   'Nikon', 'NORMAL',    'NIKKOR Z 14-30mm f/4',   'SN-NIKON-L-1430-001',    'Good',      'Available', 'Wide-angle zoom for Z-mount',          1),
-- Lenses – Nikon – TELEPHOTO
('Lens',   'Nikon', 'TELEPHOTO', 'NIKKOR Z 70-200mm f/2.8','SN-NIKON-L-70200-001',   'Excellent', 'Available', 'Telephoto zoom for Z-mount',           1),
('Lens',   'Nikon', 'TELEPHOTO', 'NIKKOR Z 100-400mm f/4.5','SN-NIKON-L-100400-001', 'Good',      'Available', 'Super-telephoto zoom for Z-mount',     1);

-- ── sub_equipment ─────────────────────────────────────────────────────────────
-- type: Battery Camera | Charger Battery | Speedlight | SD Card/CF Card | Tripod | Lain-Lain
INSERT INTO sub_equipment (type, equipment_type, camera_model, brand, capacity, total_quantity, used_quantity, available_quantity, notes) VALUES
-- Battery Camera
('Battery Camera',  'Canon', '["EOS R5", "EOS R6 II"]', 'Canon', 1, 12, 4,  8, 'Batteries for Canon EOS R5 & R6 II'),
('Battery Camera',  'Nikon', '["Z8", "Z6 III"]',         'Nikon', 1, 10, 3,  7, 'Batteries for Nikon Z8 & Z6 III'),
-- Charger Battery
('Charger Battery', 'Canon', '["EOS R5", "EOS R6 II"]', 'Canon', 1,  5, 1,  4, 'Charger for Canon LP-E6NH battery'),
('Charger Battery', 'Nikon', '["Z8", "Z6 III"]',         'Nikon', 1,  4, 1,  3, 'Charger for Nikon EN-EL15c battery'),
-- Speedlight
('Speedlight', 'Speedlight', NULL, NULL, 1,  4, 1,  3, 'Speedlight unit'),
('Speedlight', 'Speedlight', NULL, NULL, 1,  3, 0,  3, 'Speedlight unit'),
-- SD Card / CF Card (capacity = storage size in GB)
('SD Card/CF Card', 'SD Card', NULL, NULL,  8,  15, 5, 10, 'SD Card 8GB'),
('SD Card/CF Card', 'SD Card', NULL, NULL, 16,  12, 3,  9, 'SD Card 16GB'),
('SD Card/CF Card', 'SD Card', NULL, NULL, 32,  10, 4,  6, 'SD Card 32GB'),
('SD Card/CF Card', 'CF Card', NULL, NULL,  2,   8, 2,  6, 'CF Card 2GB'),
('SD Card/CF Card', 'CF Card', NULL, NULL,  4,   6, 1,  5, 'CF Card 4GB'),
('SD Card/CF Card', 'CF Card', NULL, NULL, 16,   5, 2,  3, 'CF Card 16GB'),
-- Tripod
('Tripod', 'Tripod', NULL, NULL, 1,  4, 1,  3, 'Camera tripod'),
('Tripod', 'Tripod', NULL, NULL, 1,  3, 0,  3, 'Camera tripod'),
-- Lain-Lain
('Lain-Lain', 'Kain Microfiber', NULL, NULL, 1, 20, 5, 15, 'Microfiber cleaning cloth'),
('Lain-Lain', 'Blower',          NULL, NULL, 1,  8, 2,  6, 'Air blower for sensor & lens cleaning'),
('Lain-Lain', 'Wire Transfer',   NULL, NULL, 1,  6, 1,  5, 'USB-C data transfer cable'),
('Lain-Lain', 'Gimbal',          NULL, NULL, 1,  3, 1,  2, '3-axis camera stabiliser'),
('Lain-Lain', 'Card Reader',     NULL, NULL, 1, 10, 3,  7, 'Multi-slot USB-C card reader');
