-- ============================================================
-- XTRM Wallet Withdrawal (enhancement to F-03):
-- partner_withdrawal — one row per COMPLETED wallet cash-out (bank or card) via UserWithdrawFund.
-- History/audit ONLY — does NOT touch reward_wallets / the ledger (those were debited at redemption;
-- the XTRM wallet is XTRM-side). Stores no card/PAN/CVV/PAT — only a masked destination label.
-- ============================================================
CREATE TABLE partner_withdrawal (
    id                          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id                   UUID          NOT NULL REFERENCES clients(id),
    user_id                     UUID          NOT NULL REFERENCES users(id),
    amount_gross                NUMERIC(19,2) NOT NULL,                 -- debited from wallet (XTRM TotalAmount)
    fee                         NUMERIC(19,2) NOT NULL,                 -- XTRM withdrawal fee
    amount_net                  NUMERIC(19,2) NOT NULL,                 -- delivered = gross - fee (XTRM Amount)
    currency                    VARCHAR(3)    NOT NULL,
    destination_type            VARCHAR(10)   NOT NULL,                 -- BANK | CARD
    destination_label           VARCHAR(100),                          -- masked, e.g. "Wells Fargo ••1898"
    destination_ref             UUID,                                  -- our partner_linked_bank / partner_linked_card id
    xtrm_payment_transaction_id VARCHAR(100),                          -- XTRM PaymentTransactionId
    status                      VARCHAR(20)   NOT NULL,                -- COMPLETED | FAILED
    deleted                     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version                     BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_partner_withdrawal_user ON partner_withdrawal(client_id, user_id);
