CREATE TABLE client_redemption_configs (
    id                          UUID            NOT NULL DEFAULT uuid_generate_v4(),
    client_id                   UUID            NOT NULL,
    catalog_item_id             UUID            NOT NULL,
    is_enabled                  BOOLEAN         NOT NULL DEFAULT TRUE,
    processing_mode_override    VARCHAR(50),
    minimum_wallet_balance      NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    minimum_transaction_amount  NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    return_window_days          INTEGER,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_client_redemption_configs     PRIMARY KEY (id),
    CONSTRAINT fk_crc_client                    FOREIGN KEY (client_id)       REFERENCES clients (id),
    CONSTRAINT fk_crc_catalog_item              FOREIGN KEY (catalog_item_id) REFERENCES redemption_catalog_items (id),
    CONSTRAINT uq_crc_client_catalog_item       UNIQUE (client_id, catalog_item_id)
);

CREATE INDEX idx_crc_client_id
    ON client_redemption_configs (client_id);
