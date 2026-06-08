-- ─────────────────────────────────────────────────────────────────────────────
-- V1: Auth & user management schema
-- Tables: users, roles, user_roles, refresh_tokens,
--         password_reset_tokens, email_verification_tokens
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE users (
    id                    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username              VARCHAR(50)  NOT NULL UNIQUE,
    email                 VARCHAR(255) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(100),
    phone_number          VARCHAR(20),
    bank_name             VARCHAR(100) NULL,
    account_no            VARCHAR(50)  NULL,
    signature             MEDIUMTEXT   NULL,
    profile_picture       MEDIUMTEXT,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    is_email_verified     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_locked             BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    last_login_at         TIMESTAMP    NULL,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_email    ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_active   ON users(is_active);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE roles (
    id   BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE refresh_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE password_reset_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_password_reset_tokens_token      ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id    ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE email_verification_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    used_at    DATETIME     NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_email_verification_tokens_user_id    ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens(expires_at);
