-- V6: Add ROLE_STUDENT and backfill users who must always hold it.
-- Business rule: ADMIN, HIGH_COMMITTEE, EQUIPMENT_COMMITTEE always imply ROLE_STUDENT.
-- EVENT_COMMITTEE membership type is ambiguous — NOT backfilled (admin must declare explicitly).
-- GUEST users are never automatically Students.

-- ── 1. Insert the new role ────────────────────────────────────────────────────
INSERT INTO roles (name) VALUES ('ROLE_STUDENT');

-- ── 2. Backfill: assign ROLE_STUDENT to every user who currently has
--      ROLE_ADMIN, ROLE_HIGH_COMMITTEE, or ROLE_EQUIPMENT_COMMITTEE
--      and does NOT already have ROLE_STUDENT (idempotency guard). ─────────
INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT ur.user_id, (SELECT id FROM roles WHERE name = 'ROLE_STUDENT')
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_HIGH_COMMITTEE', 'ROLE_EQUIPMENT_COMMITTEE')
  AND ur.user_id NOT IN (
      SELECT ur2.user_id
      FROM user_roles ur2
      JOIN roles r2 ON r2.id = ur2.role_id
      WHERE r2.name = 'ROLE_STUDENT'
  );
