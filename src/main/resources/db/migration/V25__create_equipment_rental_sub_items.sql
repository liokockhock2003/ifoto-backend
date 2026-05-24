CREATE TABLE equipment_rental_sub_items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    equipment_rental_id BIGINT NOT NULL,
    sub_equipment_id    BIGINT NOT NULL,
    borrowed_quantity   INT NOT NULL,
    created_at          DATETIME NOT NULL,
    updated_at          DATETIME NOT NULL,
    CONSTRAINT fk_ersi_rental        FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id) ON DELETE CASCADE,
    CONSTRAINT fk_ersi_sub_equipment FOREIGN KEY (sub_equipment_id)   REFERENCES sub_equipment(sub_equipment_id)
);

CREATE INDEX idx_ersi_rental_id        ON equipment_rental_sub_items(equipment_rental_id);
CREATE INDEX idx_ersi_sub_equipment_id ON equipment_rental_sub_items(sub_equipment_id);
