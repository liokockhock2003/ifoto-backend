-- V12: Email verification support + ROLE_STUDENT
-- Note: is_email_verified column is defined in V1 (CREATE TABLE users).

-- 1. Token table for email verification (mirrors password_reset_tokens)
CREATE TABLE email_verification_tokens (
    id          BIGINT          NOT NULL AUTO_INCREMENT,
    user_id     BIGINT          NOT NULL,
    token       VARCHAR(512)    NOT NULL,
    expires_at  DATETIME        NOT NULL,
    used        BOOLEAN         NOT NULL DEFAULT FALSE,
    used_at     DATETIME        NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_email_verification_tokens_token (token),
    KEY idx_email_verification_tokens_user_id (user_id),
    KEY idx_email_verification_tokens_expires_at (expires_at),

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Note: ROLE_STUDENT is inserted by V6 (renamed from ROLE_CLUB_MEMBER).
