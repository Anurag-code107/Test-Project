-- ============================================================
-- XTRM Redemption Payout — Multiple Bank Accounts (enhancement to F-03):
-- partner_linked_bank — one row per XTRM bank/ACH beneficiary a user has linked.
-- Cached locally so the Payout tab lists banks with a fast DB read (no XTRM GetLinkedBankAccounts per view).
-- The DEFAULT bank is NOT stored here: it stays on partner_redemption.partner_linked_bank_id, so the
-- payout path is unchanged and "one default" is guaranteed by a single value.
-- Stores only the XTRM BeneficiaryId reference + a masked label — never the account/routing number.
-- ============================================================
CREATE TABLE partner_linked_bank (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           UUID          NOT NULL REFERENCES clients(id),
    user_id             UUID          NOT NULL REFERENCES users(id),
    xtrm_beneficiary_id VARCHAR(100)  NOT NULL,                    -- XTRM BeneficiaryId (ref only, not the account #)
    masked_label        VARCHAR(100)  NOT NULL,                    -- masked display label (e.g. "Wells Fargo ••1898")
    currency            VARCHAR(3)    NOT NULL DEFAULT 'USD',      -- v1 USD-only; stored for forward-compat
    country_iso2        VARCHAR(2)    NOT NULL DEFAULT 'US',       -- v1 US-only; stored for forward-compat
    withdraw_type       VARCHAR(20)   NOT NULL DEFAULT 'ACH',      -- ACH (US) / WIRE (intl, deferred)
    deleted             BOOLEAN       NOT NULL DEFAULT FALSE,      -- soft-delete (XTRM holds the hard delete)
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0
);

-- Hot path: per-user bank listing on the Payout tab.
CREATE INDEX idx_partner_linked_bank_user ON partner_linked_bank(client_id, user_id);

-- One ACTIVE row per (tenant, user, beneficiary). PARTIAL (WHERE deleted = false) so a re-add after a
-- soft-delete can't collide if XTRM ever reissues a BeneficiaryId.
CREATE UNIQUE INDEX uq_partner_linked_bank_active
    ON partner_linked_bank(client_id, user_id, xtrm_beneficiary_id)
    WHERE deleted = false;

-- Backfill: migrate each existing single linked bank into a row (it becomes that user's default, which
-- stays pointed to by partner_redemption.partner_linked_bank_id — no data loss). v1 = USD/US/ACH.
INSERT INTO partner_linked_bank (client_id, user_id, xtrm_beneficiary_id, masked_label, currency, country_iso2, withdraw_type)
SELECT client_id, user_id, partner_linked_bank_id, COALESCE(linked_bank_label, 'Bank account'), 'USD', 'US', 'ACH'
FROM partner_redemption
WHERE partner_linked_bank_id IS NOT NULL;
