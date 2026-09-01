-- Submission idempotency: client-supplied key prevents duplicate reservations on retry.
-- Nullable — existing rows have no key; new submissions MAY supply one.
-- Unique per (client_id, user_id) scope so keys are isolated across tenants and users.
ALTER TABLE redemption_requests
    ADD COLUMN client_idempotency_key VARCHAR(255) NULL;

CREATE UNIQUE INDEX idx_redemption_requests_idempotency
    ON redemption_requests (client_id, user_id, client_idempotency_key)
    WHERE client_idempotency_key IS NOT NULL;

-- Dispatch attempt tracking: set before calling the vendor, NULL means never attempted.
-- Allows recovery to distinguish "never dispatched" (safe to retry) from
-- "dispatched but outcome ambiguous" (manual reconciliation only — no auto-retry).
ALTER TABLE redemption_requests
    ADD COLUMN dispatch_attempted_at TIMESTAMP NULL;
