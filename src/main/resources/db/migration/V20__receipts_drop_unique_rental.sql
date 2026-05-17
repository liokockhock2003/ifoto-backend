DROP PROCEDURE IF EXISTS migrate_v20;

DELIMITER $
CREATE PROCEDURE migrate_v20()
BEGIN
    DECLARE unique_index_exists INT DEFAULT 0;
    DECLARE fk_name VARCHAR(255) DEFAULT NULL;

    SELECT COUNT(*) INTO unique_index_exists
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'receipts'
      AND COLUMN_NAME = 'equipment_rental_id' AND NON_UNIQUE = 0;

    SELECT CONSTRAINT_NAME INTO fk_name
    FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'receipts'
      AND COLUMN_NAME = 'equipment_rental_id'
      AND REFERENCED_TABLE_NAME = 'equipment_rentals'
    LIMIT 1;

    IF unique_index_exists > 0 THEN
        IF fk_name IS NOT NULL THEN
            SET @drop_fk = CONCAT('ALTER TABLE receipts DROP FOREIGN KEY ', fk_name);
            PREPARE stmt FROM @drop_fk;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;

        ALTER TABLE receipts DROP INDEX equipment_rental_id;
        ALTER TABLE receipts ADD INDEX idx_receipts_rental_id (equipment_rental_id);

        IF fk_name IS NOT NULL THEN
            SET @add_fk = CONCAT('ALTER TABLE receipts ADD CONSTRAINT ', fk_name, ' FOREIGN KEY (equipment_rental_id) REFERENCES equipment_rentals(id)');
            PREPARE stmt FROM @add_fk;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
        END IF;
    END IF;
END$
DELIMITER ;

CALL migrate_v20();
DROP PROCEDURE IF EXISTS migrate_v20;
