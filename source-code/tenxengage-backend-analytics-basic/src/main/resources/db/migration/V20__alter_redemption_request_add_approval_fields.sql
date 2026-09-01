-- ============================================================
-- F-04 Redemption Approval Queue: Extend redemption_requests
-- ============================================================

ALTER TABLE redemption_requests
    ADD COLUMN IF NOT EXISTS reviewed_by      UUID         REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS reviewed_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(1000);

-- Index to support approval queue query (client_id + status = PENDING_APPROVAL)
-- idx_redemption_requests_client_status already exists from V16; no new index needed.
