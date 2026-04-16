-- V7: Add ROLE_CLUB_MEMBER and backfill users who must always hold it.
-- Business rule: ADMIN, HIGH_COMMITTEE, EQUIPMENT_COMMITTEE are always Club Members.
-- EVENT_COMMITTEE membership type is ambiguous — NOT backfilled (admin must declare explicitly).
-- GUEST users are never automatically Club Members.

-- ── 1. Insert the new role ────────────────────────────────────────────────────
INSERT INTO roles (name) VALUES ('ROLE_CLUB_MEMBER');

-- ── 2. Backfill: assign ROLE_CLUB_MEMBER to every user who currently has
--      ROLE_ADMIN, ROLE_HIGH_COMMITTEE, or ROLE_EQUIPMENT_COMMITTEE
--      and does NOT already have ROLE_CLUB_MEMBER (idempotency guard). ─────────
INSERT INTO user_roles (user_id, role_id)
SELECT DISTINCT ur.user_id, (SELECT id FROM roles WHERE name = 'ROLE_CLUB_MEMBER')
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
WHERE r.name IN ('ROLE_ADMIN', 'ROLE_HIGH_COMMITTEE', 'ROLE_EQUIPMENT_COMMITTEE')
  AND ur.user_id NOT IN (
      SELECT ur2.user_id
      FROM user_roles ur2
      JOIN roles r2 ON r2.id = ur2.role_id
      WHERE r2.name = 'ROLE_CLUB_MEMBER'
  );
