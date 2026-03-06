-- ─────────────────────────────────────────────────────────────────
-- V3: Create refresh_tokens table
-- Stores issued refresh tokens for revocation + expiry validation
-- ─────────────────────────────────────────────────────────────────

CREATE TABLE refresh_tokens (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(512)    NOT NULL UNIQUE,
    expires_at  DATETIME        NOT NULL,
    revoked     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);