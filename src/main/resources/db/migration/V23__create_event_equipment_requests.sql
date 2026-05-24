CREATE TABLE event_equipment_requests (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_number       VARCHAR(20) UNIQUE,
    event_id             BIGINT NOT NULL,
    requested_by         BIGINT NOT NULL,
    reviewed_by          BIGINT,
    status               ENUM('PENDING_REVIEW','APPROVED','REJECTED','CANCELLED','ACTIVE','RETURNED') NOT NULL,
    requested_start_date DATE NOT NULL,
    requested_end_date   DATE NOT NULL,
    approved_start_date  DATE,
    approved_end_date    DATE,
    duration_days        INT,
    rejection_reason     TEXT,
    committee_notes      TEXT,
    requester_notes      TEXT,
    approved_at          DATETIME,
    active_at            DATETIME,
    returned_at          DATETIME,
    due_return_date      DATE,
    created_at           DATETIME NOT NULL,
    updated_at           DATETIME NOT NULL,
    CONSTRAINT fk_eer_event        FOREIGN KEY (event_id)     REFERENCES events(event_id) ON DELETE CASCADE,
    CONSTRAINT fk_eer_requested_by FOREIGN KEY (requested_by) REFERENCES users(id),
    CONSTRAINT fk_eer_reviewed_by  FOREIGN KEY (reviewed_by)  REFERENCES users(id)
);

CREATE INDEX idx_eer_event_id     ON event_equipment_requests(event_id);
CREATE INDEX idx_eer_requested_by ON event_equipment_requests(requested_by);
CREATE INDEX idx_eer_status       ON event_equipment_requests(status);
