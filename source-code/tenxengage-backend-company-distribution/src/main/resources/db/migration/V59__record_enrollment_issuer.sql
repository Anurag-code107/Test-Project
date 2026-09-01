-- Which XTRM account enrolled each seller.
--
-- WHY THIS IS PERMANENT
--
-- XTRM binds a user to whoever called Register/CreateUser for them, and refuses a second user with the same
-- email address ("Email Already Exists") even under a different issuer. So a seller's issuer cannot be
-- changed by re-enrolling them — this column records a fact, not a preference.
--
-- A partner company may only pay sellers it created itself. The platform may pay any of them, which is why
-- personal redemption is unaffected by this change.
--
-- WHY EXISTING ROWS ARE LEFT NULL
--
-- Every existing row was enrolled by the platform, because that is the only thing the code has ever done.
-- It is tempting to backfill them with the platform's account number, but that value comes from
-- XTRM_ISSUER_ACCOUNT and differs between environments — a literal here would be correct in one and wrong
-- in the others, silently.
--
-- It is also unnecessary. The only question this column answers is "did THIS company enrol this seller?",
-- and for a legacy row the answer is no however it is stored. NULL therefore means "enrolled before
-- company-scoped enrollment existed" and is refused on the vendor rails, which is the same outcome a
-- correct backfill would produce — without depending on deployment configuration being guessed right here.

ALTER TABLE partner_redemption
    ADD COLUMN enrolled_issuer_account_number VARCHAR(50);

COMMENT ON COLUMN partner_redemption.enrolled_issuer_account_number IS
    'XTRM account that created this seller''s PAT. Permanent — XTRM refuses a second user with the same '
    'email, so a seller cannot be re-enrolled under a different issuer. NULL means enrolled by the platform '
    'before company-scoped enrollment existed, and cannot receive company payouts.';

-- Reading this per recipient is on the distribution eligibility path.
CREATE INDEX idx_partner_redemption_issuer
    ON partner_redemption (enrolled_issuer_account_number);
