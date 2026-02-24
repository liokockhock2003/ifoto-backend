-- V2__seed_test_data.sql
-- Test seed data for development/testing purposes only

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
    last_login_at,
    roles
) VALUES
(
    'admin',
    'admin@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',  -- password: "password"
    'Admin User',
    '+601234567890',
    NULL,
    TRUE,
    FALSE,
    0,
    NULL,
    '["USER","ADMIN"]'
),
(
    'johndoe',
    'john@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',  -- password: "password"
    'John Doe',
    '+601987654321',
    NULL,
    TRUE,
    FALSE,
    0,
    NULL,
    '["USER"]'
),
(
    'janedoe',
    'jane@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',  -- password: "password"
    'Jane Doe',
    '+601122334455',
    NULL,
    TRUE,
    FALSE,
    0,
    NULL,
    '["USER"]'
),
(
    'lockeduser',
    'locked@ifoto.com',
    '$2a$10$ZD9aWXB7zzi0YZakRGfk7OvcQY7J1eQAC7PvqWN4sNpy7ofrY4IkC',  -- password: "password"
    'Locked User',
    NULL,
    NULL,
    TRUE,
    TRUE,                -- is_locked = true (for testing locked account flow)
    5,
    NULL,
    '["USER"]'
);