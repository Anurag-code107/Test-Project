-- ============================================================
-- F-09 Balance Expiration: BalanceExpirationPolicy entity table
-- ============================================================
CREATE TABLE balance_expiration_policies (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id         UUID          NOT NULL REFERENCES clients(id),
    currency_id       VARCHAR(50)   NOT NULL,
    enabled           BOOLEAN       NOT NULL DEFAULT false,
    expiration_mode   VARCHAR(20)   NOT NULL,            -- ExpirationMode: INACTIVITY | FIXED_DATE
    inactivity_days   INTEGER       NULL,
    fixed_expiry_date DATE          NULL,
    lead_time_days    INTEGER       NOT NULL DEFAULT 30,
    enabled_at        TIMESTAMPTZ   NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted           BOOLEAN       NOT NULL DEFAULT false,
    version           BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_balance_expiration_policies_client_id
    ON balance_expiration_policies(client_id);
CREATE UNIQUE INDEX uq_balance_expiration_policies_client_currency
    ON balance_expiration_policies(client_id, currency_id);
CREATE INDEX idx_balance_expiration_policies_enabled
    ON balance_expiration_policies(enabled)
    WHERE enabled = true AND deleted = false;

-- ============================================================
-- F-09 Balance Expiration: BalanceExpiryNotice entity table
-- ============================================================
CREATE TABLE balance_expiry_notices (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID          NOT NULL REFERENCES clients(id),
    wallet_id             UUID          NOT NULL REFERENCES reward_wallets(id),
    currency_id           VARCHAR(50)   NOT NULL,
    policy_id             UUID          NOT NULL REFERENCES balance_expiration_policies(id),
    scheduled_expiry_date DATE          NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',  -- ExpiryNoticeStatus
    notified_at           TIMESTAMPTZ   NULL,
    notified_amount       NUMERIC(18,2) NULL,
    expired_at            TIMESTAMPTZ   NULL,
    expired_amount        NUMERIC(18,2) NULL,
    ledger_entry_id       UUID          NULL REFERENCES ledger_entries(id),
    cancelled_at          TIMESTAMPTZ   NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted               BOOLEAN       NOT NULL DEFAULT false,
    version               BIGINT        NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_balance_expiry_notices_event
    ON balance_expiry_notices(wallet_id, currency_id, scheduled_expiry_date);   -- idempotency key (FR-09.8)
CREATE INDEX idx_balance_expiry_notices_status_date
    ON balance_expiry_notices(status, scheduled_expiry_date);
CREATE INDEX idx_balance_expiry_notices_client
    ON balance_expiry_notices(client_id);
CREATE INDEX idx_balance_expiry_notices_policy
    ON balance_expiry_notices(policy_id);

-- ============================================================
-- F-09: supporting index for ledger-derived last-activity lookup
-- (complements idx_ledger_entries_client_currency_type from V27)
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_ledger_entries_wallet_currency_created
    ON ledger_entries(client_id, reward_wallet_id, currency_id, created_at);  -- findLastActivityAt filters by reward_wallet_id
