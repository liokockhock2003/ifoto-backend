-- ─────────────────────────────────────────────────────────────────────────────
-- V5: Seed data (development / local)
-- Insertion order follows FK dependency chain.
-- All user passwords: "password" (BCrypt strength 10)
-- ─────────────────────────────────────────────────────────────────────────────

-- ── 1. Roles ──────────────────────────────────────────────────────────────────
INSERT INTO roles (name) VALUES
('ROLE_ADMIN'),
('ROLE_NON_STUDENT'),
('ROLE_EVENT_COMMITTEE'),
('ROLE_HIGH_COMMITTEE'),
('ROLE_EQUIPMENT_COMMITTEE'),
('ROLE_STUDENT');

-- ── 2. Users ──────────────────────────────────────────────────────────────────
INSERT INTO users (username, email, password_hash, full_name, phone_number, profile_picture,
                   is_active, is_email_verified, is_locked, failed_login_attempts, last_login_at)
VALUES
('admin',               'admin@ifoto.com',               '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'Admin User',          '+601234567890', NULL, TRUE,  TRUE,  FALSE, 0, NULL),
('johndoe',             'liohock@graduate.utm.my',       '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'John Doe',            '+601987654321', NULL, TRUE,  TRUE,  FALSE, 0, NULL),
('janedoe',             'jane@ifoto.com',                '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'Jane Doe',            '+601122334455', NULL, TRUE,  TRUE,  FALSE, 0, NULL),
('lockeduser',          'locked@ifoto.com',              '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'Locked User',         NULL,            NULL, TRUE,  TRUE,  TRUE,  5, NULL),
('guestuser',           'guest@ifoto.com',               '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'Guest User',          NULL,            NULL, TRUE,  TRUE,  FALSE, 0, NULL),
('eventcommittee',      'liokockhock@gmail.com',         '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'Event Committee',     '+601112223333', NULL, TRUE,  TRUE,  FALSE, 0, NULL),
('highcommittee',       'highcommittee@ifoto.com',       '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'High Committee',      '+601444555666', NULL, TRUE,  TRUE,  FALSE, 0, NULL),
('equipmentcommittee',  'equipmentcommittee@ifoto.com',  '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC', 'Equipment Committee', '+601777888999', NULL, TRUE,  TRUE,  FALSE, 0, NULL);

-- ── 3. Role assignments ───────────────────────────────────────────────────────
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin'              AND r.name = 'ROLE_ADMIN';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin'              AND r.name = 'ROLE_STUDENT';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'johndoe'            AND r.name = 'ROLE_STUDENT';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'janedoe'            AND r.name = 'ROLE_STUDENT';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'lockeduser'         AND r.name = 'ROLE_NON_STUDENT';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'guestuser'          AND r.name = 'ROLE_NON_STUDENT';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'eventcommittee'     AND r.name = 'ROLE_EVENT_COMMITTEE';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'eventcommittee'     AND r.name = 'ROLE_STUDENT';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'highcommittee'      AND r.name = 'ROLE_HIGH_COMMITTEE';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'highcommittee'      AND r.name = 'ROLE_STUDENT';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'equipmentcommittee' AND r.name = 'ROLE_EQUIPMENT_COMMITTEE';
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'equipmentcommittee' AND r.name = 'ROLE_STUDENT';

-- ── 4. Rental pricing categories & rates ─────────────────────────────────────
INSERT INTO rental_pricing_category (name) VALUES
('CAMERA'),
('SPEEDLIGHT'),
('LENS_NORMAL'),
('LENS_TELE');

INSERT INTO rental_pricing (pricing_category_id, member_type, rate_1_day, rate_3_days, rate_per_day_extra, late_penalty_per_day)
SELECT id, 'STUDENT',      55.00,  150.00, 25.00, 20.00 FROM rental_pricing_category WHERE name = 'CAMERA'      UNION ALL
SELECT id, 'STUDENT',      15.00,   30.00,  5.00, 20.00 FROM rental_pricing_category WHERE name = 'SPEEDLIGHT'  UNION ALL
SELECT id, 'STUDENT',      25.00,   60.00, 20.00, 20.00 FROM rental_pricing_category WHERE name = 'LENS_NORMAL' UNION ALL
SELECT id, 'STUDENT',      90.00,  225.00, 50.00, 20.00 FROM rental_pricing_category WHERE name = 'LENS_TELE'   UNION ALL
SELECT id, 'NON_STUDENT', 110.00,  350.00, 75.00, 40.00 FROM rental_pricing_category WHERE name = 'CAMERA'      UNION ALL
SELECT id, 'NON_STUDENT',  40.00,   90.00, 15.00, 40.00 FROM rental_pricing_category WHERE name = 'SPEEDLIGHT'  UNION ALL
SELECT id, 'NON_STUDENT',  40.00,   90.00, 30.00, 40.00 FROM rental_pricing_category WHERE name = 'LENS_NORMAL' UNION ALL
SELECT id, 'NON_STUDENT', 110.00,  360.00, 60.00, 40.00 FROM rental_pricing_category WHERE name = 'LENS_TELE';

-- ── 5. Main equipment ─────────────────────────────────────────────────────────
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, status, notes, is_for_rent)
VALUES
-- Cameras
('Camera', 'Canon', NULL,        'EOS R5',                  'SN-CANON-R5-001',        'Excellent', 'Available', 'High-res full-frame mirrorless',       1),
('Camera', 'Canon', NULL,        'EOS R6 II',               'SN-CANON-R6II-001',      'Good',      'Available', 'Sports & event mirrorless body',        1),
('Camera', 'Nikon', NULL,        'Z8',                      'SN-NIKON-Z8-001',        'Excellent', 'Available', 'High-res Z-mount mirrorless',           1),
('Camera', 'Nikon', NULL,        'Z6 III',                  'SN-NIKON-Z6III-001',     'Good',      'Available', 'Hybrid photo/video mirrorless body',    1),
-- Lenses – Canon – PRIME (not for rent)
('Lens', 'Canon', 'PRIME',     'RF 50mm f/1.2L',          'SN-CANON-L-50-001',      'Excellent', 'Available', 'Flagship Canon prime lens',             0),
('Lens', 'Canon', 'PRIME',     'RF 85mm f/1.2L DS',       'SN-CANON-L-85-001',      'Good',      'Available', 'Portrait prime with DS coating',        0),
-- Lenses – Canon – NORMAL
('Lens', 'Canon', 'NORMAL',    'RF 24-70mm f/2.8L',       'SN-CANON-L-2470-001',    'Excellent', 'Available', 'Standard zoom for Canon RF-mount',      1),
('Lens', 'Canon', 'NORMAL',    'RF 15-35mm f/2.8L',       'SN-CANON-L-1535-001',    'Good',      'Available', 'Wide-angle zoom for Canon RF',          1),
-- Lenses – Canon – TELEPHOTO
('Lens', 'Canon', 'TELEPHOTO', 'RF 70-200mm f/2.8L',      'SN-CANON-L-70200-001',   'Excellent', 'Available', 'Telephoto zoom for Canon RF',           1),
('Lens', 'Canon', 'TELEPHOTO', 'RF 100-500mm f/4.5L',     'SN-CANON-L-100500-001',  'Good',      'Available', 'Super-telephoto zoom for Canon RF',     1),
-- Lenses – Nikon – PRIME (not for rent)
('Lens', 'Nikon', 'PRIME',     'NIKKOR Z 50mm f/1.2',     'SN-NIKON-L-50-001',      'Excellent', 'Available', 'Flagship Nikon Z prime lens',           0),
('Lens', 'Nikon', 'PRIME',     'NIKKOR Z 85mm f/1.2',     'SN-NIKON-L-85-001',      'Good',      'Available', 'Portrait prime for Z-mount',            0),
-- Lenses – Nikon – NORMAL
('Lens', 'Nikon', 'NORMAL',    'NIKKOR Z 24-70mm f/2.8',  'SN-NIKON-L-2470-001',    'Excellent', 'Available', 'Standard zoom for Z-mount',             1),
('Lens', 'Nikon', 'NORMAL',    'NIKKOR Z 14-30mm f/4',    'SN-NIKON-L-1430-001',    'Good',      'Available', 'Wide-angle zoom for Z-mount',           1),
-- Lenses – Nikon – TELEPHOTO
('Lens', 'Nikon', 'TELEPHOTO', 'NIKKOR Z 70-200mm f/2.8', 'SN-NIKON-L-70200-001',   'Excellent', 'Available', 'Telephoto zoom for Z-mount',            1),
('Lens', 'Nikon', 'TELEPHOTO', 'NIKKOR Z 100-400mm f/4.5','SN-NIKON-L-100400-001',  'Good',      'Available', 'Super-telephoto zoom for Z-mount',      1);

-- Link main_equipment to pricing categories
UPDATE main_equipment me JOIN rental_pricing_category rpc ON rpc.name = 'CAMERA'
    SET me.pricing_category_id = rpc.id WHERE me.equipment_type = 'Camera';
UPDATE main_equipment me JOIN rental_pricing_category rpc ON rpc.name = 'LENS_NORMAL'
    SET me.pricing_category_id = rpc.id WHERE me.equipment_type = 'Lens' AND me.lens_type = 'NORMAL';
UPDATE main_equipment me JOIN rental_pricing_category rpc ON rpc.name = 'LENS_TELE'
    SET me.pricing_category_id = rpc.id WHERE me.equipment_type = 'Lens' AND me.lens_type = 'TELEPHOTO';
-- PRIME lenses intentionally left NULL (not available for rental pricing)

-- ── 6. Sub equipment ──────────────────────────────────────────────────────────
INSERT INTO sub_equipment (type, equipment_type, camera_model, brand, capacity, total_quantity, notes, pricing_category_id, is_for_rent)
VALUES
-- Battery Camera
('Battery Camera',  'Canon',      '["EOS R5", "EOS R6 II"]', 'Canon', 1, 12, 'Batteries for Canon EOS R5 & R6 II',   NULL, 0),
('Battery Camera',  'Nikon',      '["Z8", "Z6 III"]',         'Nikon', 1, 10, 'Batteries for Nikon Z8 & Z6 III',      NULL, 0),
-- Charger Battery
('Charger Battery', 'Canon',      '["EOS R5", "EOS R6 II"]', 'Canon', 1,  5, 'Charger for Canon LP-E6NH battery',    NULL, 0),
('Charger Battery', 'Nikon',      '["Z8", "Z6 III"]',         'Nikon', 1,  4, 'Charger for Nikon EN-EL15c battery',   NULL, 0),
-- Speedlight (linked to SPEEDLIGHT pricing below)
('Speedlight',      'Speedlight', NULL,                        NULL,   1,  4, 'Speedlight unit',                      NULL, 0),
('Speedlight',      'Speedlight', NULL,                        NULL,   1,  3, 'Speedlight unit',                      NULL, 0),
-- SD Card / CF Card
('SD Card/CF Card', 'SD Card',    NULL, NULL,  8, 15, 'SD Card 8GB',   NULL, 0),
('SD Card/CF Card', 'SD Card',    NULL, NULL, 16, 12, 'SD Card 16GB',  NULL, 0),
('SD Card/CF Card', 'SD Card',    NULL, NULL, 32, 10, 'SD Card 32GB',  NULL, 0),
('SD Card/CF Card', 'CF Card',    NULL, NULL,  2,  8, 'CF Card 2GB',   NULL, 0),
('SD Card/CF Card', 'CF Card',    NULL, NULL,  4,  6, 'CF Card 4GB',   NULL, 0),
('SD Card/CF Card', 'CF Card',    NULL, NULL, 16,  5, 'CF Card 16GB',  NULL, 0),
-- Tripod
('Tripod',          'Tripod',     NULL, NULL, 1,  4, 'Camera tripod',  NULL, 0),
('Tripod',          'Tripod',     NULL, NULL, 1,  3, 'Camera tripod',  NULL, 0),
-- Lain-Lain
('Lain-Lain', 'Kain Microfiber', NULL, NULL, 1, 20, 'Microfiber cleaning cloth',         NULL, 0),
('Lain-Lain', 'Blower',          NULL, NULL, 1,  8, 'Air blower for sensor & lens cleaning', NULL, 0),
('Lain-Lain', 'Wire Transfer',   NULL, NULL, 1,  6, 'USB-C data transfer cable',         NULL, 0),
('Lain-Lain', 'Gimbal',          NULL, NULL, 1,  3, '3-axis camera stabiliser',          NULL, 0),
('Lain-Lain', 'Card Reader',     NULL, NULL, 1, 10, 'Multi-slot USB-C card reader',       NULL, 0);

-- Link speedlights to SPEEDLIGHT pricing
UPDATE sub_equipment s JOIN rental_pricing_category rpc ON rpc.name = 'SPEEDLIGHT'
    SET s.pricing_category_id = rpc.id, s.is_for_rent = 1
    WHERE s.type = 'Speedlight';

-- ── 7. Events ─────────────────────────────────────────────────────────────────
INSERT INTO events (event_name, description, start_date, end_date, location, is_active) VALUES
(
    'Annual Photography Exhibition 2026',
    'A showcase of the best photography works from club members throughout the year.',
    '2026-05-01', '2026-05-03', 'Main Hall, KL Convention Centre', TRUE
),
(
    'Night Photography Workshop',
    'Hands-on workshop covering long exposure and astrophotography techniques.',
    '2026-06-15', '2026-06-15', 'Titiwangsa Lake Garden, Kuala Lumpur', TRUE
);

-- ── 8. Event committee assignments ───────────────────────────────────────────
INSERT INTO event_committee (event_id, user_id)
SELECT e.event_id, u.id FROM events e, users u
WHERE e.event_name = 'Annual Photography Exhibition 2026' AND u.username = 'eventcommittee';

INSERT INTO event_committee (event_id, user_id)
SELECT e.event_id, u.id FROM events e, users u
WHERE e.event_name = 'Annual Photography Exhibition 2026' AND u.username = 'highcommittee';

INSERT INTO event_committee (event_id, user_id)
SELECT e.event_id, u.id FROM events e, users u
WHERE e.event_name = 'Night Photography Workshop' AND u.username = 'eventcommittee';
