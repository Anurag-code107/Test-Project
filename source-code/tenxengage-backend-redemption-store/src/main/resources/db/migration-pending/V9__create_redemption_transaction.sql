CREATE TABLE redemption_transactions (
    id                      UUID            NOT NULL DEFAULT uuid_generate_v4(),
    client_id               UUID            NOT NULL,
    wallet_id               UUID            NOT NULL,
    catalog_item_id         UUID            NOT NULL,
    requested_by            UUID            NOT NULL,
    amount                  NUMERIC(19, 4)  NOT NULL,
    currency_type           VARCHAR(50)     NOT NULL,
    status                  VARCHAR(50)     NOT NULL,
    processing_mode         VARCHAR(50)     NOT NULL,
    provider_name           VARCHAR(50)     NOT NULL,
    provider_transaction_id VARCHAR(255),
    approved_by             UUID,
    approved_at             TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    failure_reason          TEXT,
    return_id               UUID,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_redemption_transactions   PRIMARY KEY (id),
    CONSTRAINT fk_rt_client                 FOREIGN KEY (client_id)       REFERENCES clients (id),
    CONSTRAINT fk_rt_wallet                 FOREIGN KEY (wallet_id)       REFERENCES reward_wallets (id),
    CONSTRAINT fk_rt_catalog_item           FOREIGN KEY (catalog_item_id) REFERENCES redemption_catalog_items (id)
);

CREATE INDEX idx_rt_client_requested_by
    ON redemption_transactions (client_id, requested_by);

CREATE INDEX idx_rt_client_status
    ON redemption_transactions (client_id, status);
