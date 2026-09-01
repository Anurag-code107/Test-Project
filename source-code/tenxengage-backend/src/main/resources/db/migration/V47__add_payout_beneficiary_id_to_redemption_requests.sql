-- Bank-transfer redemptions can target a SPECIFIC linked bank chosen at submit time (multi-bank).
-- Holds the XTRM beneficiary id of the chosen bank; NULL → the after-commit dispatch falls back to the
-- user's default (partner_redemption.partner_linked_bank_id). A reference only — never an account number.
ALTER TABLE redemption_requests
    ADD COLUMN payout_beneficiary_id VARCHAR(100);
