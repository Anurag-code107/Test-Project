-- Company admin details, and the schema changes that let a PENDING XTRM row hold partial progress.
--
-- WHY THE COLUMNS RELAX
--
-- Beneficiary/CreateBeneficiary returns the company's account number AND its pseudo credentials, but no
-- wallet id — and it returns the secret exactly once. The call cannot be replayed for the same company,
-- because the name is taken on the second attempt. So provisioning persists what it has the moment it has
-- it, and enriches afterwards.
--
-- Two columns therefore have to be nullable:
--   * a CLAIM row, written inside the company-create transaction, knows nothing yet. It exists so that
--     uq_xtrm_account_per_company — not the vendor — settles concurrent provisioning attempts. Claim last
--     instead of first and two attempts both reach CreateBeneficiary, creating a second real beneficiary
--     company at XTRM that we then discard and can never delete.
--   * a row between CreateBeneficiary and wallet discovery holds everything except the wallet.
--
-- It is also what gives last_error somewhere to land when provisioning fails before an SPN exists.
--
-- THE CONNECTED-MEANS-PAYABLE INVARIANT IS NOT WEAKENED
--
-- V57 refused CONNECTED without credentials, because that state "would fail only at dispatch, after money
-- is reserved". That reasoning applies just as much to a missing account number or wallet. The old
-- constraint is dropped and replaced by a wider one below rather than quietly redefined, so anyone reading
-- the schema sees that the rule changed instead of finding the same name meaning something new.

-- 1. Default company admin. Contact details for XTRM, not a platform user.
--    Nullable: every existing company has none, and a company can legitimately exist with no payout intent.
ALTER TABLE partner_companies
    ADD COLUMN admin_first_name    VARCHAR(100),
    ADD COLUMN admin_last_name     VARCHAR(100),
    ADD COLUMN admin_email         VARCHAR(255),
    ADD COLUMN admin_mobile_number VARCHAR(20),
    ADD COLUMN admin_city          VARCHAR(100),
    ADD COLUMN admin_region        VARCHAR(100),
    ADD COLUMN admin_postal_code   VARCHAR(20),
    -- VARCHAR(2), not CHAR(2). Every other ISO2 column here is VARCHAR (V34, V35, V38), and Hibernate
    -- generates VARCHAR for @Column(length = 2) — CHAR would make the unit-test schema, which is built
    -- from the entities, differ in type from this one.
    ADD COLUMN admin_country_iso2  VARCHAR(2);

COMMENT ON COLUMN partner_companies.admin_email IS
    'Default company admin. Contact details for XTRM BeneficiaryCompanyAdminDetails, not a platform user. '
    'Supplied as an all-or-nothing group with the other admin_* columns.';

-- 2. Let a row exist before anything about it is known.
ALTER TABLE partner_company_xtrm_accounts
    ALTER COLUMN xtrm_account_number DROP NOT NULL,
    ALTER COLUMN xtrm_wallet_id      DROP NOT NULL;

-- 3. What CreateBeneficiary tells us beyond identity and credentials.
ALTER TABLE partner_company_xtrm_accounts
    ADD COLUMN account_identity_level VARCHAR(30),
    ADD COLUMN xtrm_beneficiary_name  VARCHAR(255);

COMMENT ON COLUMN partner_company_xtrm_accounts.account_identity_level IS
    'XTRM KYC tier for this account, e.g. Basic. Gated by redemption.xtrm.acceptable-identity-levels, '
    'which is empty (permissive) by default.';
COMMENT ON COLUMN partner_company_xtrm_accounts.xtrm_beneficiary_name IS
    'The name actually sent as BeneficiaryCompanyName — disambiguated per tenant, so not always the '
    'company name. Without it our row cannot be matched to XTRM''s portal.';

-- 4. Replace, do not redefine.
ALTER TABLE partner_company_xtrm_accounts
    DROP CONSTRAINT chk_xtrm_account_connected_has_credentials;

ALTER TABLE partner_company_xtrm_accounts
    ADD CONSTRAINT chk_xtrm_account_connected_is_payable CHECK (
        status <> 'CONNECTED'
        OR (encrypted_credentials IS NOT NULL
            AND xtrm_account_number IS NOT NULL
            AND xtrm_wallet_id      IS NOT NULL)
    );
