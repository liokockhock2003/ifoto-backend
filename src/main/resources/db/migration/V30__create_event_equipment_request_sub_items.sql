CREATE TABLE event_equipment_request_sub_items (
    id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_equipment_request_id  BIGINT NOT NULL,
    sub_equipment_id            BIGINT NOT NULL,
    borrowed_quantity           INT    NOT NULL,
    created_at                  DATETIME NOT NULL,
    updated_at                  DATETIME NOT NULL,
    CONSTRAINT fk_eer_sub_request   FOREIGN KEY (event_equipment_request_id) REFERENCES event_equipment_requests(id),
    CONSTRAINT fk_eer_sub_equipment FOREIGN KEY (sub_equipment_id) REFERENCES sub_equipment(sub_equipment_id)
);
