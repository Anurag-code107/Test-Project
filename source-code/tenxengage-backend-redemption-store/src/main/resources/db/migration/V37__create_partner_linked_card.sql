-- ============================================================
-- XTRM Card instruments (enhancement to F-03):
-- partner_linked_card — one row per XTRM card a user has linked (multi-card). Mirrors partner_linked_bank.
-- A card is a DUAL-PURPOSE instrument: payout rail (TransferFund + CardToken) AND withdrawal destination.
-- ⚠️ PCI: stores ONLY the XTRM CardToken + masked last-4 + type/status — NEVER the card number (PAN),
--    CVV, or full expiry.
-- ============================================================
CREATE TABLE partner_linked_card (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id     UUID          NOT NULL REFERENCES clients(id),
    user_id       UUID          NOT NULL REFERENCES users(id),
    card_token    VARCHAR(100)  NOT NULL,                     -- XTRM CardToken (a reference, NOT the card number)
    masked_last4  VARCHAR(4),                                 -- last 4 digits only (PCI-allowed)
    card_type     VARCHAR(30),                                -- e.g. "Visa Card"
    status        VARCHAR(20),                                -- e.g. "Approved"
    deleted       BOOLEAN       NOT NULL DEFAULT FALSE,       -- soft-delete (XTRM DeleteCard holds the hard delete)
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version       BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_partner_linked_card_user ON partner_linked_card(client_id, user_id);

-- One ACTIVE row per (tenant, user, token); PARTIAL so a re-add after soft-delete can't collide.
CREATE UNIQUE INDEX uq_partner_linked_card_active
    ON partner_linked_card(client_id, user_id, card_token)
    WHERE deleted = false;

-- Default card for the CARD payout rail (mirrors partner_redemption.partner_linked_bank_id).
ALTER TABLE partner_redemption ADD COLUMN partner_linked_card_id VARCHAR(100) NULL;  -- default card's CardToken
ALTER TABLE partner_redemption ADD COLUMN linked_card_label      VARCHAR(100) NULL;  -- masked display label
