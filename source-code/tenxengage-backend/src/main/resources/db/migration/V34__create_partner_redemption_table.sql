-- ============================================================
-- XTRM Redemption Payout & Enrollment (enhancement to F-03):
-- PartnerRedemption — maps a platform user -> their XTRM recipient id + payout config.
-- No `deleted` column: 1:1 system record, not user-visible recoverable content.
-- Stores only XTRM reference ids + the address XTRM CreateUser requires — never bank/card numbers.
-- ============================================================
CREATE TABLE partner_redemption (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id           UUID          NOT NULL REFERENCES clients(id),
    user_id             UUID          NOT NULL REFERENCES users(id),
    recipient_user_id   VARCHAR(50)   NULL,                       -- XTRM PAT (from CreateUser)
    enrollment_status   VARCHAR(30)   NOT NULL DEFAULT 'NOT_ENROLLED', -- XtrmEnrollmentStatus
    enrollment_error    VARCHAR(500)  NULL,                       -- sanitized last error (no PII)
    identity_level      VARCHAR(30)   NULL,                       -- XTRM AccountIdentityLevel
    address_line1       VARCHAR(255)  NULL,                       -- required to enroll (payee); PII
    address_line2       VARCHAR(255)  NULL,
    city                VARCHAR(120)  NULL,
    region              VARCHAR(120)  NULL,
    postal_code         VARCHAR(20)   NULL,
    country_iso2        VARCHAR(2)    NULL,                       -- required to enroll; 2-letter ISO
    payout_method       VARCHAR(30)   NOT NULL DEFAULT 'ANYPAY',  -- RedemptionPayoutMethod
    partner_linked_bank_id VARCHAR(100)  NULL,                       -- XTRM BeneficiaryId (ref only, not the account #)
    linked_bank_label   VARCHAR(100)  NULL,                       -- masked display label
    enrolled_at         TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    version             BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX        idx_partner_redemption_client_id     ON partner_redemption(client_id);
CREATE UNIQUE INDEX uq_partner_redemption_user_id        ON partner_redemption(user_id);
CREATE INDEX        idx_partner_redemption_client_status ON partner_redemption(client_id, enrollment_status);
