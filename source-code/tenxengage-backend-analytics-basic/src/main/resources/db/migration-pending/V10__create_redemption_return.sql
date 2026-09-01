CREATE TABLE redemption_returns (
    id                          UUID            NOT NULL DEFAULT uuid_generate_v4(),
    client_id                   UUID            NOT NULL,
    redemption_transaction_id   UUID            NOT NULL,
    requested_by                UUID            NOT NULL,
    status                      VARCHAR(50)     NOT NULL,
    return_reason               TEXT,
    reviewed_by                 UUID,
    reviewed_at                 TIMESTAMPTZ,
    provider_return_id          VARCHAR(255),
    provider_confirmed_at       TIMESTAMPTZ,
    wallet_credited_at          TIMESTAMPTZ,
    rejection_reason            TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_redemption_returns                PRIMARY KEY (id),
    CONSTRAINT fk_rr_client                         FOREIGN KEY (client_id)                 REFERENCES clients (id),
    CONSTRAINT fk_rr_redemption_transaction         FOREIGN KEY (redemption_transaction_id) REFERENCES redemption_transactions (id),
    CONSTRAINT uq_rr_redemption_transaction_id      UNIQUE (redemption_transaction_id)
);

CREATE INDEX idx_rr_client_id
    ON redemption_returns (client_id);
