-- V5: Add active role to users for strict role-switching semantics

ALTER TABLE users
    ADD COLUMN active_role_id BIGINT NULL;

ALTER TABLE users
    ADD CONSTRAINT fk_users_active_role
        FOREIGN KEY (active_role_id) REFERENCES roles(id);

CREATE INDEX idx_users_active_role ON users(active_role_id);

-- Backfill active role from any existing assigned role (smallest role_id per user)
UPDATE users u
LEFT JOIN (
    SELECT user_id, MIN(role_id) AS role_id
    FROM user_roles
    GROUP BY user_id
) ur ON ur.user_id = u.id
SET u.active_role_id = ur.role_id
WHERE u.active_role_id IS NULL;
