ALTER TABLE payments ADD CONSTRAINT payments_ibfk_1 FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id);
