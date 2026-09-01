CREATE TABLE ledger_entries (
    id                      UUID            NOT NULL DEFAULT uuid_generate_v4(),
    client_id               UUID            NOT NULL,
    wallet_id               UUID            NOT NULL,
    entry_type              VARCHAR(50)     NOT NULL,
    source_type             VARCHAR(50)     NOT NULL,
    currency_type           VARCHAR(50)     NOT NULL,
    amount                  NUMERIC(19, 4)  NOT NULL,
    related_transaction_id  UUID,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_ledger_entries            PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_client     FOREIGN KEY (client_id)  REFERENCES clients (id),
    CONSTRAINT fk_ledger_entries_wallet     FOREIGN KEY (wallet_id)  REFERENCES reward_wallets (id),
    CONSTRAINT chk_ledger_entries_amount    CHECK (amount > 0)
);

-- ledger history: most recent entries first per wallet
CREATE INDEX idx_ledger_entries_wallet_history
    ON ledger_entries (wallet_id, created_at DESC);

-- transaction reconciliation: all entries linked to one transaction
CREATE INDEX idx_ledger_entries_client_transaction
    ON ledger_entries (client_id, related_transaction_id);
