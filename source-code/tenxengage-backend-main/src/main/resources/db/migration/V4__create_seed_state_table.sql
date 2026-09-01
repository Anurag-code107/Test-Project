-- V4: Seed State Tracking Table
-- Tracks seeding progress across restarts to enable incremental seeding.
-- Keys stored: seed.last_seeded_date, seed.last_seeded_quarter,
-- seed.partner_count, seed.seed_version, seed.mode

CREATE TABLE seed_state (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    state_key   VARCHAR(100) NOT NULL,
    state_value TEXT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (client_id, state_key)
);

CREATE INDEX idx_seed_state_client ON seed_state(client_id);
