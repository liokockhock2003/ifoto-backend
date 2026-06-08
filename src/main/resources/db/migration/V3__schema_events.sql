-- ─────────────────────────────────────────────────────────────────────────────
-- V3: Events schema
-- Tables: events, event_committee
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE events (
    event_id        BIGINT       AUTO_INCREMENT PRIMARY KEY,
    event_name      VARCHAR(255) NOT NULL,
    description     TEXT,
    start_datetime  DATETIME     NOT NULL,
    end_datetime    DATETIME     NOT NULL,
    location        VARCHAR(255),
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_events_is_active       ON events(is_active);
CREATE INDEX idx_events_start_datetime  ON events(start_datetime);

-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE event_committee (
    event_id BIGINT NOT NULL,
    user_id  BIGINT NOT NULL,
    PRIMARY KEY (event_id, user_id),
    CONSTRAINT fk_event_committee_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE,
    CONSTRAINT fk_event_committee_user  FOREIGN KEY (user_id)  REFERENCES users(id)         ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_event_committee_event_id ON event_committee(event_id);
CREATE INDEX idx_event_committee_user_id  ON event_committee(user_id);
