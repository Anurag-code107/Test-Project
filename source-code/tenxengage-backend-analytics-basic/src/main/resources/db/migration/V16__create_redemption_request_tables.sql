-- ============================================================
-- F-03 Redemption Flow: Schema
-- ============================================================

-- 1. Add max_in_flight_redemptions to existing tenant_redemption_settings
ALTER TABLE tenant_redemption_settings
    ADD COLUMN IF NOT EXISTS max_in_flight_redemptions INTEGER NOT NULL DEFAULT 10;

-- 2. Create redemption_requests table
CREATE TABLE redemption_requests (
    id                       UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                UUID          NOT NULL REFERENCES clients(id),
    wallet_id                UUID          NOT NULL REFERENCES reward_wallets(id),
    user_id                  UUID          NOT NULL REFERENCES users(id),
    catalog_item_id          UUID          NOT NULL REFERENCES redemption_catalog_items(id),
    amount                   NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency_id              VARCHAR(50)   NOT NULL,
    wallet_type              VARCHAR(20)   NOT NULL,
    status                   VARCHAR(30)   NOT NULL,
    processing_mode          VARCHAR(30)   NOT NULL,
    category                 VARCHAR(20)   NOT NULL,
    vendor_reference_id      VARCHAR(255),
    reserve_ledger_entry_id  UUID          REFERENCES ledger_entries(id),
    debit_ledger_entry_id    UUID          REFERENCES ledger_entries(id),
    release_ledger_entry_id  UUID          REFERENCES ledger_entries(id),
    scheduled_batch_date     DATE,
    submitted_at             TIMESTAMPTZ   NOT NULL,
    processing_started_at    TIMESTAMPTZ,
    completed_at             TIMESTAMPTZ,
    failure_reason           VARCHAR(500),
    version                  BIGINT        NOT NULL DEFAULT 0,
    deleted                  BOOLEAN       NOT NULL DEFAULT false,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_redemption_requests_client_id     ON redemption_requests(client_id);
CREATE INDEX idx_redemption_requests_client_status ON redemption_requests(client_id, status);
CREATE INDEX idx_redemption_requests_user_id       ON redemption_requests(client_id, user_id);
CREATE INDEX idx_redemption_requests_wallet_id     ON redemption_requests(wallet_id);

-- 3. Create redemption_webhook_events table
CREATE TABLE redemption_webhook_events (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID          NOT NULL REFERENCES clients(id),
    vendor                VARCHAR(20)   NOT NULL,
    redemption_request_id UUID          NOT NULL REFERENCES redemption_requests(id),
    idempotency_key       VARCHAR(255)  NOT NULL,
    payload               JSONB         NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    received_at           TIMESTAMPTZ   NOT NULL,
    processed_at          TIMESTAMPTZ,
    failure_reason        VARCHAR(1000),
    version               BIGINT        NOT NULL DEFAULT 0,
    deleted               BOOLEAN       NOT NULL DEFAULT false,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_webhook_events_idempotency_key        ON redemption_webhook_events(idempotency_key);
CREATE INDEX        idx_webhook_events_client_id             ON redemption_webhook_events(client_id);
CREATE INDEX        idx_webhook_events_redemption_request_id ON redemption_webhook_events(redemption_request_id);
CREATE INDEX        idx_webhook_events_status                ON redemption_webhook_events(status);
