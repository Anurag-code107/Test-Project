-- ============================================================
-- F-06 Non-Cash Returns: RedemptionReturn entity table
-- ============================================================
CREATE TABLE redemption_returns (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               UUID          NOT NULL REFERENCES clients(id),
    redemption_id           UUID          NOT NULL REFERENCES redemption_requests(id),
    partner_user_id         UUID          NOT NULL,
    status                  TEXT          NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason                  TEXT          NULL,
    reviewed_by             UUID          NULL,
    reviewed_at             TIMESTAMPTZ   NULL,
    review_notes            TEXT          NULL,
    vendor_return_reference VARCHAR(255)  NULL,
    amount                  NUMERIC(19,4) NOT NULL,
    currency_id             VARCHAR(50)   NOT NULL,
    approved_at             TIMESTAMPTZ   NULL,
    timed_out_at            TIMESTAMPTZ   NULL,
    confirmed_at            TIMESTAMPTZ   NULL,
    rejected_at             TIMESTAMPTZ   NULL,
    cancelled_at            TIMESTAMPTZ   NULL,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted                 BOOLEAN       NOT NULL DEFAULT false,
    version                 BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_redemption_returns_client_id     ON redemption_returns(client_id);
CREATE INDEX idx_redemption_returns_client_status ON redemption_returns(client_id, status);
CREATE INDEX idx_redemption_returns_redemption_id ON redemption_returns(redemption_id);
CREATE INDEX idx_redemption_returns_partner_user  ON redemption_returns(client_id, partner_user_id);
CREATE INDEX idx_redemption_returns_vendor_ref    ON redemption_returns(vendor_return_reference)
    WHERE vendor_return_reference IS NOT NULL;
