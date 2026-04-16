-- V5: Create password_reset_tokens table
-- Stores one-time tokens for forgotten password flow

CREATE TABLE password_reset_tokens (
    id          BIGINT          AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(512)    NOT NULL UNIQUE,
    expires_at  DATETIME        NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    used_at     DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_token      ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_user_id    ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens(expires_at);
