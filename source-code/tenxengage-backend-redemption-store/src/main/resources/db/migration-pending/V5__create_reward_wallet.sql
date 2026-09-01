CREATE TABLE reward_wallets (
    id                  UUID            NOT NULL DEFAULT uuid_generate_v4(),
    client_id           UUID            NOT NULL,
    owner_type          VARCHAR(50)     NOT NULL,
    user_id             UUID,
    partner_company_id  UUID,
    currency_type       VARCHAR(50)     NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    available_balance   NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    reserved_balance    NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    total_earned        NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_reward_wallets          PRIMARY KEY (id),
    CONSTRAINT fk_reward_wallets_client   FOREIGN KEY (client_id) REFERENCES clients (id)
);

-- tenant + owner lookups
CREATE INDEX idx_reward_wallets_client_id
    ON reward_wallets (client_id);

CREATE INDEX idx_reward_wallets_lookup
    ON reward_wallets (client_id, owner_type, currency_type);

-- one wallet per individual user per currency, within a tenant
CREATE UNIQUE INDEX uq_reward_wallet_individual_currency
    ON reward_wallets (client_id, user_id, currency_type)
    WHERE user_id IS NOT NULL;

-- one wallet per partner company per currency, within a tenant
CREATE UNIQUE INDEX uq_reward_wallet_company_currency
    ON reward_wallets (client_id, partner_company_id, currency_type)
    WHERE partner_company_id IS NOT NULL;
