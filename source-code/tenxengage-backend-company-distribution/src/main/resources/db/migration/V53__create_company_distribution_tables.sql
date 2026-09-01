-- Company Distribution Store — the two tables that record a distribution.
--
-- Division of labour with redemption_requests:
--   * company_distributions       — INTENT and grouping: who sent what, on which rail, and why.
--   * company_distribution_items  — one row PER RECIPIENT. This is where the users who received a
--                                   distribution are stored, for all three rails.
--   * redemption_requests         — the vendor payout LIFECYCLE, for the two rails that leave the
--                                   platform. Reused because the XTRM webhook, settle(), reconciliation
--                                   and crash recovery are all keyed on it.
--
-- The WALLET_CREDIT rail has no vendor and no webhook, so it creates NO redemption_requests row: money
-- that never leaves the platform must not be counted as redeemed (it would be counted again when the
-- seller later redeems it). That rail's lifecycle lives on the item itself.

CREATE TABLE company_distributions (
    id                     UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id              UUID          NOT NULL REFERENCES clients(id),
    partner_company_id     UUID          NOT NULL REFERENCES partner_companies(id),
    source_wallet_id       UUID          NOT NULL REFERENCES reward_wallets(id),
    rail                   VARCHAR(20)   NOT NULL,
    catalog_item_id        UUID          NULL REFERENCES redemption_catalog_items(id),
    currency_id            VARCHAR(50)   NOT NULL,
    initiated_by_user_id   UUID          NOT NULL REFERENCES users(id),
    recipient_count        INT           NOT NULL CHECK (recipient_count > 0),
    total_amount           NUMERIC(19,4) NOT NULL CHECK (total_amount > 0),
    note                   VARCHAR(500)  NULL,
    client_idempotency_key VARCHAR(255)  NULL,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- A gift card needs a SKU; the other two rails have no catalog concept of their own.
    -- (BANK_TRANSFER's reserved per-client card is recorded on the redemption leg, not here.)
    CONSTRAINT chk_distribution_catalog_item CHECK (
        (rail = 'GIFT_CARD' AND catalog_item_id IS NOT NULL)
        OR (rail <> 'GIFT_CARD' AND catalog_item_id IS NULL)
    )
);

-- A re-POST with the same key returns the original distribution instead of sending twice.
CREATE UNIQUE INDEX uq_company_distributions_idem
    ON company_distributions(client_id, client_idempotency_key)
    WHERE client_idempotency_key IS NOT NULL;

-- Distribution History: every distribution drawn from one company's wallet, newest first.
CREATE INDEX idx_company_distributions_company
    ON company_distributions(client_id, partner_company_id, created_at DESC);

COMMENT ON TABLE company_distributions IS
    'One row per distribution the partner admin submitted. total_amount is what was REQUESTED; the amount '
    'that actually moved is SUM(amount) over COMPLETED items, which differs after a partial failure.';


CREATE TABLE company_distribution_items (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id               UUID          NOT NULL REFERENCES clients(id),
    distribution_id         UUID          NOT NULL REFERENCES company_distributions(id),
    recipient_user_id       UUID          NOT NULL REFERENCES users(id),
    amount                  NUMERIC(19,4) NOT NULL CHECK (amount > 0),

    -- GIFT_CARD / BANK_TRANSFER: the payout leg owns the status. Read it from there, never duplicated here.
    redemption_request_id   UUID          NULL REFERENCES redemption_requests(id),

    -- WALLET_CREDIT only: this rail has no payout leg, so it carries its own lifecycle.
    -- RESERVED -> COMPLETED | FAILED.
    status                  VARCHAR(20)   NULL,
    debit_ledger_entry_id   UUID          NULL REFERENCES ledger_entries(id),
    credit_ledger_entry_id  UUID          NULL REFERENCES ledger_entries(id),
    release_ledger_entry_id UUID          NULL REFERENCES ledger_entries(id),
    failure_reason          VARCHAR(500)  NULL,
    settled_at              TIMESTAMPTZ   NULL,

    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    -- Exactly one lifecycle owner: a payout leg, or this row's own status. Never both, never neither —
    -- which is what keeps "status is derived, never stored twice" true.
    CONSTRAINT chk_distribution_item_leg CHECK (
        (redemption_request_id IS NOT NULL AND status IS NULL)
        OR (redemption_request_id IS NULL AND status IS NOT NULL)
    )
);

-- A recipient cannot appear twice in one distribution (would double-pay them).
CREATE UNIQUE INDEX uq_distribution_item_recipient
    ON company_distribution_items(distribution_id, recipient_user_id);

-- Company Award History: "what did I receive?"
CREATE INDEX idx_distribution_items_recipient
    ON company_distribution_items(client_id, recipient_user_id, created_at DESC);

-- Distribution detail: "who did I distribute to?"
CREATE INDEX idx_distribution_items_distribution
    ON company_distribution_items(distribution_id);

-- Drives the stuck-item sweep. Partial, so it stays tiny: only unsettled wallet-credit items match, and
-- rows leave the index as they settle. Without this sweep a crash mid-settlement would leave a recipient's
-- share reserved on the company wallet forever — the existing recovery sweep only scans redemption_requests.
CREATE INDEX idx_distribution_items_unsettled
    ON company_distribution_items(client_id, status)
    WHERE status = 'RESERVED';

COMMENT ON TABLE company_distribution_items IS
    'One row per recipient of a distribution — the record of WHO was paid, on every rail. Payout rails '
    'point at redemption_request_id and read status from there; WALLET_CREDIT has no payout leg and owns '
    'its own status plus its debit/credit/release ledger ids.';
