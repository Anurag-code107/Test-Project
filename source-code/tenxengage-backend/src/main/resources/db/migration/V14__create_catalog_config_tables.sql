-- Client Admin per-item tenant configuration
CREATE TABLE client_catalog_item_configs (
    id                              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                       UUID          NOT NULL REFERENCES clients(id),
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    redemption_catalog_item_id      UUID          NOT NULL REFERENCES redemption_catalog_items(id),
    enabled                         BOOLEAN       NOT NULL DEFAULT false,
    processing_mode_override        VARCHAR(30)   NULL,
    min_transaction_amount_override DECIMAL(18,2) NULL CHECK (min_transaction_amount_override > 0),
    min_wallet_balance_override     DECIMAL(18,2) NULL CHECK (min_wallet_balance_override >= 0),
    return_window_days_override     INT           NULL CHECK (return_window_days_override >= 0),
    version                         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_client_catalog_item_config
        UNIQUE (client_id, redemption_catalog_item_id)
);

CREATE INDEX idx_client_catalog_item_configs_client_id
    ON client_catalog_item_configs(client_id);
CREATE INDEX idx_client_catalog_item_configs_client_enabled
    ON client_catalog_item_configs(client_id, enabled);

-- Client Admin per-item per-region availability overrides
CREATE TABLE client_catalog_region_configs (
    id                              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                       UUID         NOT NULL REFERENCES clients(id),
    created_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    redemption_catalog_item_id      UUID         NOT NULL REFERENCES redemption_catalog_items(id),
    region_code                     VARCHAR(10)  NOT NULL,
    enabled                         BOOLEAN      NOT NULL,
    CONSTRAINT uq_client_catalog_region_config
        UNIQUE (client_id, redemption_catalog_item_id, region_code)
);

CREATE INDEX idx_client_catalog_region_configs_client_item
    ON client_catalog_region_configs(client_id, redemption_catalog_item_id);
CREATE INDEX idx_client_catalog_region_configs_client_region
    ON client_catalog_region_configs(client_id, region_code);
