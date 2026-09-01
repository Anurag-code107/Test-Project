CREATE TABLE tenant_redemption_settings (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID         NOT NULL REFERENCES clients(id),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    batch_cadence VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    CONSTRAINT uq_tenant_redemption_settings_client UNIQUE (client_id)
);

CREATE INDEX idx_tenant_redemption_settings_client_id
    ON tenant_redemption_settings(client_id);
