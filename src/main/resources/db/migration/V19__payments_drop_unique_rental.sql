DROP PROCEDURE IF EXISTS migrate_v19;

DELIMITER $$
CREATE PROCEDURE migrate_v19()
BEGIN
    DECLARE unique_index_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO unique_index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'payments'
      AND INDEX_NAME = 'equipment_rental_id'
      AND NON_UNIQUE = 0;

    IF unique_index_exists > 0 THEN
        ALTER TABLE payments DROP FOREIGN KEY payments_ibfk_1;
        ALTER TABLE payments DROP INDEX equipment_rental_id;
        ALTER TABLE payments ADD INDEX idx_payments_rental_id (equipment_rental_id);
        ALTER TABLE payments ADD CONSTRAINT payments_ibfk_1 FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id);
    END IF;
END$$
DELIMITER ;

CALL migrate_v19();
DROP PROCEDURE IF EXISTS migrate_v19;
