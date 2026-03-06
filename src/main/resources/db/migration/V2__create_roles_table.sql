-- V2: Create roles + user_roles tables
-- Normalizes roles out of JSON column in users table into a proper relation

-- ── roles lookup table ────────────────────────────────────────────────────────
CREATE TABLE roles (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50)     NOT NULL UNIQUE  -- e.g. ROLE_USER, ROLE_ADMIN
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── join table ────────────────────────────────────────────────────────────────
CREATE TABLE user_roles (
    user_id     BIGINT  NOT NULL,
    role_id     BIGINT  NOT NULL,

    PRIMARY KEY (user_id, role_id),

    CONSTRAINT fk_user_roles_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_user_roles_role
        FOREIGN KEY (role_id) REFERENCES roles(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── indexes ───────────────────────────────────────────────────────────────────
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- ── seed default roles ────────────────────────────────────────────────────────
INSERT INTO roles (name) VALUES
    ('ROLE_ADMIN'),
    ('ROLE_CLUB_MEMBER'),
    ('ROLE_GUEST'),
    ('ROLE_EVENT_COMMITTEE'),
    ('ROLE_HIGH_COMMITTEE'),
    ('ROLE_EQUIPMENT_COMMITTEE');