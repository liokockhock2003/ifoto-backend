CREATE TABLE main_equipment_statuses (
    id                  BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    main_equipment_id   BIGINT      NOT NULL,
    status_type         VARCHAR(30) NOT NULL,
    start_date          DATE        NOT NULL,
    end_date            DATE        NOT NULL,
    notes               TEXT,
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    CONSTRAINT fk_mes_equipment FOREIGN KEY (main_equipment_id) REFERENCES main_equipment(main_equipment_id)
);
