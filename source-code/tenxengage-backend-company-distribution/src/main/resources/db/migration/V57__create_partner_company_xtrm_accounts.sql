-- Per-partner-company XTRM identity and credentials — the prerequisite for a company paying its own sellers.
--
-- WHY THIS EXISTS
--
-- XTRM will not let one account spend another account's balance. Paying a seller *from a partner company's
-- wallet* therefore has to be authenticated AS that company, using the pseudo credentials XTRM issues for it
-- — not the platform credentials we use everywhere else. Presenting the platform's credentials with a
-- company's wallet id is exactly what produced "400 Invalid wallet id".
--
--   Step 1  client  -> partner company   TransferFundToCompany   authenticated as the CLIENT   (works today)
--   Step 2  company -> seller            TransferFund            authenticated as the COMPANY  (needs this)
--
-- WHAT A ROW MEANS
--
-- A row is the statement "this partner company can pay from its own XTRM wallet". Because XTRM requires each
-- company to be separately onboarded, KYC-verified and linked to the managing account before it issues those
-- credentials, this is inherently per-company: the payout rails cannot be switched on globally. The row's
-- presence and status ARE that per-company switch — no extra flag column, and no way for the two to disagree.
--
-- CREDENTIALS ARE ENCRYPTED, AND SEPARATE FROM IDENTITY
--
-- account number and wallet id are identifiers and stay in the clear: they are needed for reconciliation, for
-- support, and for reading the table at all. The client id/secret pair is a SECRET and is stored only as an
-- AES-GCM blob (ConnectorEncryptionService), so a database dump or a careless SELECT * cannot leak the
-- ability to move a company's money. It is nullable because identity is knowable before XTRM issues
-- credentials — that is the PENDING state.

CREATE TABLE partner_company_xtrm_accounts (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id             UUID         NOT NULL REFERENCES clients(id),
    partner_company_id    UUID         NOT NULL REFERENCES partner_companies(id),

    -- XTRM's own identifiers for this company. Its SPN account number, and the wallet payouts draw from.
    xtrm_account_number   VARCHAR(50)  NOT NULL,
    xtrm_wallet_id        VARCHAR(50)  NOT NULL,

    -- AES-GCM blob of {clientId, clientSecret}. NULL while PENDING.
    encrypted_credentials TEXT         NULL,

    -- PENDING   identity known, XTRM has not issued credentials yet -> cannot pay
    -- CONNECTED credentials present and usable                      -> may pay from its own wallet
    -- DISABLED  deliberately switched off (revoked, suspended, offboarded)
    status                VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    connected_at          TIMESTAMPTZ  NULL,
    last_error            VARCHAR(500) NULL,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version               BIGINT       NOT NULL DEFAULT 0,

    -- One XTRM account per partner company. Two rows would mean two possible funding sources for the same
    -- company's payouts, with nothing to say which is authoritative.
    CONSTRAINT uq_xtrm_account_per_company UNIQUE (partner_company_id),

    -- CONNECTED without credentials is unpayable and would fail only at dispatch, after money is reserved.
    -- Refuse the state outright rather than discover it mid-payout.
    CONSTRAINT chk_xtrm_account_connected_has_credentials CHECK (
        status <> 'CONNECTED' OR encrypted_credentials IS NOT NULL
    ),
    CONSTRAINT chk_xtrm_account_status CHECK (status IN ('PENDING', 'CONNECTED', 'DISABLED'))
);

-- The dispatch path asks "can this company pay?" on every distribution.
CREATE INDEX idx_xtrm_accounts_company_status
    ON partner_company_xtrm_accounts (partner_company_id, status);

COMMENT ON TABLE partner_company_xtrm_accounts IS
    'Per-partner-company XTRM identity + pseudo credentials. A CONNECTED row is what allows that company to '
    'pay its sellers from its own XTRM wallet; credentials are AES-GCM encrypted.';
COMMENT ON COLUMN partner_company_xtrm_accounts.encrypted_credentials IS
    'AES-GCM blob of {clientId, clientSecret} — never store or log these in the clear.';
