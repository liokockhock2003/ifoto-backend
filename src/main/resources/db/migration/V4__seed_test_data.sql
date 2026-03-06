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
    profile_picture_url,
    is_active,
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
    NULL, TRUE, FALSE, 0, NULL
),
-- ROLE_CLUB_MEMBER
(
    'johndoe',
    'john@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'John Doe',
    '+601987654321',
    NULL, TRUE, FALSE, 0, NULL
),
(
    'janedoe',
    'jane@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Jane Doe',
    '+601122334455',
    NULL, TRUE, FALSE, 0, NULL
),
-- ROLE_GUEST
(
    'lockeduser',
    'locked@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Locked User',
    NULL, NULL, TRUE, TRUE, 5, NULL
),
(
    'guestuser',
    'guest@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Guest User',
    NULL, NULL, TRUE, FALSE, 0, NULL
),
-- ROLE_EVENT_COMMITTEE
(
    'eventcommittee',
    'eventcommittee@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Event Committee',
    '+601112223333',
    NULL, TRUE, FALSE, 0, NULL
),
-- ROLE_HIGH_COMMITTEE
(
    'highcommittee',
    'highcommittee@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'High Committee',
    '+601444555666',
    NULL, TRUE, FALSE, 0, NULL
),
-- ROLE_EQUIPMENT_COMMITTEE
(
    'equipmentcommittee',
    'equipmentcommittee@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',
    'Equipment Committee',
    '+601777888999',
    NULL, TRUE, FALSE, 0, NULL
);

-- ── assign roles via user_roles ───────────────────────────────────────────────
-- admin → ROLE_ADMIN + ROLE_CLUB_MEMBER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'admin'
AND r.name IN ('ROLE_ADMIN', 'ROLE_CLUB_MEMBER');

-- johndoe → ROLE_CLUB_MEMBER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'johndoe'
AND r.name = 'ROLE_CLUB_MEMBER';

-- janedoe → ROLE_CLUB_MEMBER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'janedoe'
AND r.name = 'ROLE_CLUB_MEMBER';

-- lockeduser → ROLE_GUEST (locked account test)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'lockeduser'
AND r.name = 'ROLE_GUEST';

-- guestuser → ROLE_GUEST
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'guestuser'
AND r.name = 'ROLE_GUEST';

-- eventcommittee → ROLE_EVENT_COMMITTEE + ROLE_CLUB_MEMBER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'eventcommittee'
AND r.name IN ('ROLE_EVENT_COMMITTEE', 'ROLE_CLUB_MEMBER');

-- highcommittee → ROLE_HIGH_COMMITTEE + ROLE_CLUB_MEMBER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'highcommittee'
AND r.name IN ('ROLE_HIGH_COMMITTEE', 'ROLE_CLUB_MEMBER');

-- equipmentcommittee → ROLE_EQUIPMENT_COMMITTEE + ROLE_CLUB_MEMBER
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.username = 'equipmentcommittee'
AND r.name IN ('ROLE_EQUIPMENT_COMMITTEE', 'ROLE_CLUB_MEMBER');