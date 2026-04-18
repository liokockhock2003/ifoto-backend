-- V4: Seed test/development data
-- Insert test users and assign roles via user_roles join table
-- password for all users: "password" (bcrypt hash)

-- ── seed users ────────────────────────────────────────────────────────────────
INSERT INTO users (
    username,
    email,
    password_hash,
    full_name,
    phone_number,
    profile_picture,
    is_active,
    is_email_verified,
    is_locked,
    failed_login_attempts,
    last_login_at
) VALUES
-- ROLE_ADMIN
(
    'admin',
    'admin@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Admin User',
    '+601234567890',
    NULL, TRUE, TRUE, FALSE, 0, NULL
),
(
    'johndoe',
    'john@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'John Doe',
    '+601987654321',
    NULL, TRUE, TRUE, FALSE, 0, NULL
),
(
    'janedoe',
    'jane@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Jane Doe',
    '+601122334455',
    NULL, TRUE, TRUE, FALSE, 0, NULL
),
-- ROLE_NON_STUDENT
(
    'lockeduser',
    'locked@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Locked User',
    NULL, NULL, TRUE, TRUE, TRUE, 5, NULL
),
(
    'guestuser',
    'guest@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Guest User',
    NULL, NULL, TRUE, TRUE, FALSE, 0, NULL
),
-- ROLE_EVENT_COMMITTEE
(
    'eventcommittee',
    'eventcommittee@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Event Committee',
    '+601112223333',
    NULL, TRUE, TRUE, FALSE, 0, NULL
),
-- ROLE_HIGH_COMMITTEE
(
    'highcommittee',
    'highcommittee@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'High Committee',
    '+601444555666',
    NULL, TRUE, TRUE, FALSE, 0, NULL
),
-- ROLE_EQUIPMENT_COMMITTEE
(
    'equipmentcommittee',
    'equipmentcommittee@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Equipment Committee',
    '+601777888999',
    NULL, TRUE, TRUE, FALSE, 0, NULL
);

-- ── assign roles via user_roles ───────────────────────────────────────────────
-- admin → ROLE_ADMIN
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin'
AND r.name = 'ROLE_ADMIN';

-- johndoe → ROLE_NON_STUDENT
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'johndoe'
AND r.name = 'ROLE_NON_STUDENT';

-- janedoe → ROLE_NON_STUDENT
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'janedoe'
AND r.name = 'ROLE_NON_STUDENT';

-- lockeduser → ROLE_NON_STUDENT (locked account test)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'lockeduser'
AND r.name = 'ROLE_NON_STUDENT';

-- guestuser → ROLE_NON_STUDENT
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'guestuser'
AND r.name = 'ROLE_NON_STUDENT';

-- eventcommittee → ROLE_EVENT_COMMITTEE
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'eventcommittee'
AND r.name = 'ROLE_EVENT_COMMITTEE';

-- highcommittee → ROLE_HIGH_COMMITTEE
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'highcommittee'
AND r.name = 'ROLE_HIGH_COMMITTEE';

-- equipmentcommittee → ROLE_EQUIPMENT_COMMITTEE
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'equipmentcommittee'
AND r.name = 'ROLE_EQUIPMENT_COMMITTEE';