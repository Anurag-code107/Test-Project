-- Durable per-redemption snapshot of WHERE the payout went, captured at dispatch. Independent of the mutable
-- partner_redemption.payout_method (which reflects only the user's CURRENT default) — so a payout's destination
-- stays correct even after the user changes their default rail.
--   payout_method            — RedemptionPayoutMethod enum name (VARCHAR so future rails need no migration).
--   payout_destination_label — masked label, e.g. 'Visa ••1111', 'KOTAK ••8943', 'AnyPay wallet'. Never a full PAN.
ALTER TABLE redemption_requests
    ADD COLUMN payout_method VARCHAR(30),
    ADD COLUMN payout_destination_label VARCHAR(100);
