CREATE TABLE sub_equipment_quantity_holds (
    id                  BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sub_equipment_id    BIGINT   NOT NULL,
    quantity            INT      NOT NULL,
    start_date          DATE     NOT NULL,
    end_date            DATE     NOT NULL,
    notes               TEXT,
    created_at          DATETIME NOT NULL,
    updated_at          DATETIME NOT NULL,
    CONSTRAINT fk_seqh_equipment FOREIGN KEY (sub_equipment_id) REFERENCES sub_equipment(sub_equipment_id)
);
