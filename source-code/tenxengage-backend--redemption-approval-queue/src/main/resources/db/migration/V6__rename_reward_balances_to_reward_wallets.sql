-- Rename table
ALTER TABLE reward_balances RENAME TO reward_wallets;

-- Rename balance → available_balance
ALTER TABLE reward_wallets RENAME COLUMN balance TO available_balance;

-- Make user_id nullable (COMPANY wallets have no user)
ALTER TABLE reward_wallets ALTER COLUMN user_id DROP NOT NULL;

-- Add new columns
ALTER TABLE reward_wallets ADD COLUMN reserved_balance    DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE reward_wallets ADD COLUMN wallet_type         VARCHAR(20)   NOT NULL DEFAULT 'INDIVIDUAL';
ALTER TABLE reward_wallets ADD COLUMN partner_company_id  UUID          NULL REFERENCES partner_companies(id);
ALTER TABLE reward_wallets ADD COLUMN version             BIGINT        NOT NULL DEFAULT 0;

-- Check constraint: exactly one owner per wallet type
ALTER TABLE reward_wallets ADD CONSTRAINT chk_wallet_owner CHECK (
    (wallet_type = 'INDIVIDUAL' AND user_id IS NOT NULL AND partner_company_id IS NULL)
    OR
    (wallet_type = 'COMPANY' AND partner_company_id IS NOT NULL AND user_id IS NULL)
);

-- Partial unique indexes
CREATE UNIQUE INDEX uq_reward_wallets_individual
    ON reward_wallets(client_id, user_id, currency_id)
    WHERE wallet_type = 'INDIVIDUAL';

CREATE UNIQUE INDEX uq_reward_wallets_company
    ON reward_wallets(client_id, partner_company_id, currency_id)
    WHERE wallet_type = 'COMPANY';

-- Multi-currency lookup indexes
CREATE INDEX idx_reward_wallets_client_user    ON reward_wallets(client_id, user_id);
CREATE INDEX idx_reward_wallets_client_company ON reward_wallets(client_id, partner_company_id);
