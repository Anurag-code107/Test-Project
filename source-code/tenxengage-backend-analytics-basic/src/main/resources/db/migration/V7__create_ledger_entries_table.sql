CREATE TABLE ledger_entries (
    id                        UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                 UUID          NOT NULL REFERENCES clients(id),
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    reward_wallet_id          UUID          NOT NULL REFERENCES reward_wallets(id),
    entry_type                VARCHAR(30)   NOT NULL,
    amount                    DECIMAL(18,2) NOT NULL CHECK (amount > 0),
    currency_id               VARCHAR(50)   NOT NULL,
    reference_type            VARCHAR(50)   NULL,
    reference_id              UUID          NULL,
    note                      VARCHAR(500)  NULL,
    available_balance_before  DECIMAL(18,2) NOT NULL,
    available_balance_after   DECIMAL(18,2) NOT NULL,
    reserved_balance_before   DECIMAL(18,2) NOT NULL,
    reserved_balance_after    DECIMAL(18,2) NOT NULL
);

CREATE INDEX idx_ledger_entries_client_id      ON ledger_entries(client_id);
CREATE INDEX idx_ledger_entries_wallet_id      ON ledger_entries(reward_wallet_id);
CREATE INDEX idx_ledger_entries_wallet_created ON ledger_entries(reward_wallet_id, created_at DESC);
CREATE INDEX idx_ledger_entries_reference      ON ledger_entries(reference_type, reference_id)
    WHERE reference_id IS NOT NULL;

-- Idempotency: prevent double-crediting the same earning event
CREATE UNIQUE INDEX uq_ledger_credit_idempotency
    ON ledger_entries(reward_wallet_id, reference_type, reference_id)
    WHERE reference_id IS NOT NULL AND entry_type = 'CREDIT';
