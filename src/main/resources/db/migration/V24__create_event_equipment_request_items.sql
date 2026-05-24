CREATE TABLE event_equipment_request_items (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_equipment_request_id BIGINT NOT NULL,
    main_equipment_id          BIGINT NOT NULL,
    created_at                 DATETIME NOT NULL,
    updated_at                 DATETIME NOT NULL,
    CONSTRAINT fk_eeri_request   FOREIGN KEY (event_equipment_request_id) REFERENCES event_equipment_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_eeri_equipment FOREIGN KEY (main_equipment_id)          REFERENCES main_equipment(main_equipment_id)
);

CREATE INDEX idx_eeri_request_id   ON event_equipment_request_items(event_equipment_request_id);
CREATE INDEX idx_eeri_equipment_id ON event_equipment_request_items(main_equipment_id);
