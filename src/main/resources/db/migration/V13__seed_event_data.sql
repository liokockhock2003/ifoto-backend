-- V13: Seed sample event data
-- Inserts two sample events and assigns committee members from seeded users

-- ── seed events ───────────────────────────────────────────────────────────────
INSERT INTO events (event_name, description, start_date, end_date, location, is_active) VALUES
(
    'Annual Photography Exhibition 2026',
    'A showcase of the best photography works from club members throughout the year.',
    '2026-05-01',
    '2026-05-03',
    'Main Hall, KL Convention Centre',
    TRUE
),
(
    'Night Photography Workshop',
    'Hands-on workshop covering long exposure and astrophotography techniques.',
    '2026-06-15',
    '2026-06-15',
    'Titiwangsa Lake Garden, Kuala Lumpur',
    TRUE
);

-- ── assign committee members ───────────────────────────────────────────────────
-- eventcommittee → Annual Photography Exhibition 2026
INSERT INTO event_committee (event_id, user_id)
SELECT e.event_id, u.id FROM events e, users u
WHERE e.event_name = 'Annual Photography Exhibition 2026'
AND u.username = 'eventcommittee';

-- highcommittee → Annual Photography Exhibition 2026
INSERT INTO event_committee (event_id, user_id)
SELECT e.event_id, u.id FROM events e, users u
WHERE e.event_name = 'Annual Photography Exhibition 2026'
AND u.username = 'highcommittee';

-- eventcommittee → Night Photography Workshop
INSERT INTO event_committee (event_id, user_id)
SELECT e.event_id, u.id FROM events e, users u
WHERE e.event_name = 'Night Photography Workshop'
AND u.username = 'eventcommittee';
