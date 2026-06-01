-- ─────────────────────────────────────────────────────────────────────────────
-- V5: Seed data (development / local)
-- Insertion order follows FK dependency chain.
-- All user passwords: "password" (BCrypt strength 10)
-- Equipment data sourced from: List Alatan KFK 2026
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
-- Source: List Alatan KFK 2026
-- condition : Good / Faulty

-- Canon Cameras
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Camera', 'Canon', NULL, '5D Mark IV', '********1849', 'GOOD',   'Being used for convocation',      1),
('Camera', 'Canon', NULL, '5D Mark IV', '********0528', 'GOOD',   'Being used for convocation',      1),
('Camera', 'Canon', NULL, '5D Mark IV', '********2053', 'FAIR',   'Being used for convocation',      1),
('Camera', 'Canon', NULL, '5D Mark IV', '********2100', 'GOOD',   'Being used for convocation',      1),
('Camera', 'Canon', NULL, '5D Mark II', '******4547',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '5D Mark II', '******0957',   'FAULTY', 'Cannot snap, CF card problem',    1),
('Camera', 'Canon', NULL, '5D Mark II', '******4545',   'GOOD',   'Live view capable',               1),
('Camera', 'Canon', NULL, '5D Mark II', '******2214',   'GOOD',   'Live view capable',               1),
('Camera', 'Canon', NULL, '60D',        '******8600',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '60D',        '******0470',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '60D',        '******0471',   'FAIR',   NULL,                              1),
('Camera', 'Canon', NULL, '60D',        '******0472',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '50D',        '******0164',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '50D',        '******8476',   'FAULTY', 'Error 99, cannot snap',           1),
('Camera', 'Canon', NULL, '50D',        '******8479',   'FAULTY', 'Cannot snap, screen blank',       1),
('Camera', 'Canon', NULL, '50D',        '******8477',   'GOOD',   'Recently serviced, no live view', 1),
('Camera', 'Canon', NULL, '30D',        '******2484',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '30D',        '******4122',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '30D',        '******4120',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '30D',        '******4123',   'FAIR',   NULL,                              1),
('Camera', 'Canon', NULL, '30D',        '******7685',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '30D',        '******4118',   'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, '30D',        '******4121',   'FAIR',   'Main dial less responsive',       1),
('Camera', 'Canon', NULL, 'EOS R10',    NULL,           'GOOD',   NULL,                              1),
('Camera', 'Canon', NULL, 'EOS R10',    NULL,           'GOOD',   NULL,                              1);

-- Nikon Cameras
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Camera', 'Nikon', NULL, 'D90',   '***5025', 'GOOD', NULL,                  1),
('Camera', 'Nikon', NULL, 'D90',   '***5013', 'GOOD', NULL,                  1),
('Camera', 'Nikon', NULL, 'D90',   '***5014', 'GOOD', NULL,                  1),
('Camera', 'Nikon', NULL, 'D90',   '***5024', 'GOOD', 'Recently serviced',   1),
('Camera', 'Nikon', NULL, 'D700',  '***5845', 'GOOD', NULL,                  1),
('Camera', 'Nikon', NULL, 'D7000', '***2534', 'GOOD', NULL,                  1),
('Camera', 'Nikon', NULL, 'D7000', '***5571', 'GOOD', 'Minor screen defect', 1),
('Camera', 'Nikon', NULL, 'D7000', '***7401', 'GOOD', 'Minor screen defect', 1),
('Camera', 'Nikon', NULL, 'D7000', '***5396', 'GOOD', 'Minor screen defect', 1),
('Camera', 'Nikon', NULL, 'D7000', '***5405', 'GOOD', 'Minor screen defect', 1);

-- Sony Camcorders
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Camera', 'Sony', NULL, 'Camcorder', NULL, 'GOOD', NULL, 1),
('Camera', 'Sony', NULL, 'Camcorder', NULL, 'GOOD', NULL, 1);

-- Canon Lenses (working)
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Lens', 'Canon', 'PRIME',     'EF 50mm',        NULL,         'GOOD', NULL,                                  0),
('Lens', 'Canon', 'PRIME',     'EF 50mm',        NULL,         'GOOD', NULL,                                  0),
('Lens', 'Canon', 'NORMAL',    'EF 17-40mm USM', NULL,         'GOOD', 'Being used for convocation',          1),
('Lens', 'Canon', 'NORMAL',    'EF 17-40mm USM', NULL,         'GOOD', 'Being used for convocation',          1),
('Lens', 'Canon', 'NORMAL',    'EF 17-40mm USM', NULL,         'GOOD', 'Being used for convocation',          1),
('Lens', 'Canon', 'NORMAL',    'EF 17-40mm USM', NULL,         'GOOD', 'Being used for convocation',          1),
('Lens', 'Sigma', 'NORMAL',    'DC 18-250mm',    '****5731',   'GOOD', 'Loose rubber grip',                   1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******5294', 'FAIR', NULL,                                  1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******0062', 'GOOD', NULL,                                  1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******8839', 'GOOD', 'Attachment to body issue',            1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******7880', 'GOOD', NULL,                                  1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******3692', 'GOOD', 'AF not functioning',                  1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******3617', 'FAIR', NULL,                                  1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '****2061',   'GOOD', NULL,                                  1),
('Lens', 'Canon', 'NORMAL',    'EF-S 18-55mm',   '******7877', 'GOOD', 'Previously taped, recently serviced', 1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '******1924', 'GOOD', 'Shaking noted but functioning',       1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '**4247',     'GOOD', NULL,                                  1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '******0167', 'FAIR', NULL,                                  1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '**4807',     'GOOD', NULL,                                  1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '**4259',     'FAIR', NULL,                                  1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '******9487', 'GOOD', NULL,                                  1),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm',    '******0184', 'GOOD', NULL,                                  1);

-- Nikon Lenses (working)
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Lens', 'Nikon', 'PRIME',     'AF NIKKOR 50mm',             '***1233',  'GOOD', NULL,           0),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-105mm',    '****8203', 'GOOD', NULL,           1),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-105mm',    '****8205', 'GOOD', NULL,           1),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-105mm',    '****8202', 'GOOD', NULL,           1),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-55mm',     '****9142', 'GOOD', NULL,           1),
('Lens', 'Nikon', 'NORMAL',    'AF-S N NIKKOR 16-35mm',      NULL,       'GOOD', NULL,           1),
('Lens', 'Nikon', 'NORMAL',    'AF-S N NIKKOR 16-35mm',      NULL,       'GOOD', 'Minor fungus', 1),
('Lens', 'Sigma', 'NORMAL',    'SIGMA 28-105mm D Aspherical', '***1490',  'GOOD', NULL,           1),
('Lens', 'Sigma', 'NORMAL',    'SIGMA 28-105mm D Aspherical', NULL,       'GOOD', 'Minor fungus', 1);

-- Canon Lenses (faulty — under maintenance)
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Lens', 'Sigma', 'NORMAL',    'DC 18-250mm', '****5745', 'FAULTY', 'Cannot zoom',       0),
('Lens', 'Sigma', 'NORMAL',    'DC 17-50mm',  '****1496', 'FAULTY', 'Loose rubber grip', 0),
('Lens', 'Sigma', 'NORMAL',    'DC 17-50mm',  '****6514', 'FAULTY', 'Loose rubber grip', 0),
('Lens', 'Canon', 'TELEPHOTO', 'EF 70-200mm', '**4768',   'FAULTY', 'Autofocus faulty',  0);

-- Nikon Lenses (faulty — under maintenance)
INSERT INTO main_equipment (equipment_type, brand, lens_type, model, serial_number, `condition`, problems, is_for_rent) VALUES
('Lens', 'Nikon', 'PRIME',     'AF NIKKOR 50mm',           '***8720',  'FAULTY', 'Lens error',                  0),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-105mm',  '****6854', 'FAULTY', 'Fungus',                      0),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-55mm',   '****1203', 'FAULTY', 'Cannot autofocus',            0),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-70mm',   '***9540',  'FAULTY', 'Zoom and autofocus problem',  0),
('Lens', 'Nikon', 'NORMAL',    'AF-S DX NIKKOR 18-70mm',   '***7239',  'FAULTY', 'Zoom and autofocus problem',  0),
('Lens', 'Nikon', 'TELEPHOTO', 'AF NIKKOR 75-240mm',       NULL,       'FAULTY', 'Error',                       0),
('Lens', 'Sigma', 'TELEPHOTO', 'SIGMA EX 70-200mm',        '****2104', 'FAULTY', 'Error',                       0),
('Lens', 'Sigma', 'TELEPHOTO', 'SIGMA EX 70-200mm',        '****3960', 'FAULTY', 'Error',                       0),
('Lens', 'Nikon', 'TELEPHOTO', 'AF ED NIKKOR 80-200mm',    NULL,       'FAULTY', 'Error',                       0),
('Lens', 'Nikon', 'TELEPHOTO', 'AF ED NIKKOR 80-200mm',    NULL,       'FAULTY', 'Error',                       0);

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
('Battery Camera', 'Canon', '["50D", "30D"]',                       'Canon', 1, 23, 'Batteries for Canon 50D & 30D',                   NULL, 0),
('Battery Camera', 'Canon', '["60D", "5D Mark II", "5D Mark IV"]',  'Canon', 1, 15, 'Batteries for Canon 60D, 5D Mark II, 5D Mark IV', NULL, 0),
('Battery Camera', 'Nikon', '["D90", "D700"]',                      'Nikon', 1, 12, 'Batteries for Nikon D90 & D700',                  NULL, 0),
('Battery Camera', 'Nikon', '["D7000"]',                            'Nikon', 1, 11, 'Batteries for Nikon D7000',                       NULL, 0),
-- Charger Battery
('Charger Battery', 'Canon', '["50D", "30D"]',                      'Canon', 1,  7, 'Charger for Canon 50D & 30D battery',             NULL, 0),
('Charger Battery', 'Canon', '["60D", "5D Mark II", "5D Mark IV"]', 'Canon', 1,  6, 'Charger for Canon 60D, 5D MII, 5D MIV battery',  NULL, 0),
('Charger Battery', 'Nikon', '["D90", "D700"]',                     'Nikon', 1,  1, 'Charger for Nikon D90 & D700 battery',            NULL, 0),
('Charger Battery', 'Nikon', '["D7000"]',                           'Nikon', 1,  6, 'Charger for Nikon D7000 battery',                 NULL, 0),
-- Speedlight (linked to SPEEDLIGHT pricing below)
('Speedlight', 'Speedlight', NULL, NULL, 1, 5, 'Speedlight unit',       NULL, 0),
('Speedlight', 'Speedlight', NULL, NULL, 1, 3, 'Speedlight unit (new)', NULL, 0),
-- SD Card
('SD Card/CF Card', 'SD Card', NULL, NULL,  8, 4, 'SD Card 8GB',  NULL, 0),
('SD Card/CF Card', 'SD Card', NULL, NULL, 16, 4, 'SD Card 16GB', NULL, 0),
('SD Card/CF Card', 'SD Card', NULL, NULL, 32, 5, 'SD Card 32GB', NULL, 0),
-- CF Card
('SD Card/CF Card', 'CF Card', NULL, NULL,  2, 1, 'CF Card 2GB',  NULL, 0),
('SD Card/CF Card', 'CF Card', NULL, NULL,  4, 2, 'CF Card 4GB',  NULL, 0),
('SD Card/CF Card', 'CF Card', NULL, NULL, 16, 5, 'CF Card 16GB', NULL, 0),
-- Tripod
('Tripod', 'Tripod', NULL, NULL, 1, 3, 'Camera tripod', NULL, 0),
-- Lain-lain
('Lain-Lain', 'Kain Microfiber', NULL, NULL, 1,  3, 'Microfiber cleaning cloth',           NULL, 0),
('Lain-Lain', 'Blower',          NULL, NULL, 1,  8, 'Air blower for sensor/lens cleaning', NULL, 0),
('Lain-Lain', 'Wire Transfer',   NULL, NULL, 1, 12, 'USB data transfer cable',             NULL, 0),
('Lain-Lain', 'Gimbal',          NULL, NULL, 1,  1, '3-axis camera stabiliser',            NULL, 0),
('Lain-Lain', 'Card Reader',     NULL, NULL, 1,  1, 'Multi-slot card reader',              NULL, 0),
('Lain-Lain', 'Wire HDMI',       NULL, NULL, 1,  1, 'HDMI cable',                          NULL, 0);

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
