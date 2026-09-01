-- Company Distribution Store — notification types.
--
-- Two, aimed at different people:
--
--   COMPANY_AWARD_RECEIVED  -> the SELLER, when their share actually settles. Fired on item settle, never
--                              on submit: at submit the money is only reserved, so telling the seller then
--                              would announce funds that have not arrived and may still fail.
--                              For WALLET_CREDIT this is the ONLY signal the seller ever gets — nothing
--                              external happens, no email from XTRM, no bank line. For BANK_TRANSFER it is
--                              the only signal before the money lands. For GIFT_CARD, XTRM emails the card
--                              itself, so ours is the heads-up that it is on its way.
--
--   COMPANY_DISTRIBUTION_SUMMARY -> the ADMIN, when a distribution reaches a terminal rollup. Matters most
--                              for PARTIALLY_COMPLETED: some recipients were paid and the rest had their
--                              share released back to the company wallet. Without this the admin would have
--                              to open Distribution History to discover a partial failure.
--
-- default_roles is the recipient-role allowlist used by the notification dispatcher (NOT
-- "target_roles" — the column name is default_roles; V2 seeds it positionally, which is easy to misread).

INSERT INTO notification_types (id, key, category, title, description, default_roles, created_at, updated_at)
VALUES
  (gen_random_uuid(), 'COMPANY_AWARD_RECEIVED', 'REDEMPTION',
   'Company Reward Received',
   'Your company sent you a reward',
   'PARTNER_SELLER', NOW(), NOW()),

  (gen_random_uuid(), 'COMPANY_DISTRIBUTION_SUMMARY', 'REDEMPTION',
   'Distribution Complete',
   'A company distribution finished — including any recipients that failed',
   'PARTNER_ADMIN', NOW(), NOW())
ON CONFLICT (key) DO NOTHING;
