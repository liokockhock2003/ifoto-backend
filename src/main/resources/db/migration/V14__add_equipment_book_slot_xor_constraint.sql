-- V14: Enforce XOR constraint on equipment_book_slots
-- A booking slot must be tied to exactly one equipment type:
-- either main_equipment_id (serialized item) OR sub_equipment_id (pooled item), never both or neither.

ALTER TABLE equipment_book_slots
    ADD CONSTRAINT chk_book_slot_equipment_xor
    CHECK (
        (main_equipment_id IS NOT NULL AND sub_equipment_id IS NULL)
        OR
        (main_equipment_id IS NULL     AND sub_equipment_id IS NOT NULL)
    );
