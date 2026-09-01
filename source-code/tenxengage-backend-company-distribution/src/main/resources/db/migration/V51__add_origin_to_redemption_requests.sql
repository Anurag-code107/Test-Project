-- Company Distribution Store — discriminator on the payout leg.
--
-- redemption_requests now carries payout legs for TWO stores:
--   * the redemption store      — a user redeeming their own individual wallet   (origin = SELF)
--   * the distribution store    — a partner admin distributing the company wallet (origin = COMPANY_DISTRIBUTION)
--
-- For a COMPANY_DISTRIBUTION row the usual reading of the row is INVERTED in one important way:
-- user_id is the RECIPIENT (the partner seller being paid), not the person who acted. That is
-- deliberate — it is what lets XtrmVendorService/settle()/the webhook/crash-recovery all resolve the
-- payee with no changes, since they already read request.getUserId(). The initiating admin is NOT
-- stored here; it lives on company_distributions.initiated_by_user_id, one join away via
-- company_distribution_items.redemption_request_id.
--
-- Every existing row is a self-service redemption, so the DEFAULT backfills them correctly. The
-- Java-side @Builder.Default on RedemptionOrigin matters just as much: Hibernate always includes the
-- column in its INSERT, so without a Java default it would send an explicit NULL and this NOT NULL
-- constraint would reject every personal redemption.
ALTER TABLE redemption_requests
    ADD COLUMN origin VARCHAR(30) NOT NULL DEFAULT 'SELF';

COMMENT ON COLUMN redemption_requests.origin IS
    'SELF = redemption store (user redeems their own wallet). COMPANY_DISTRIBUTION = distribution '
    'store (partner admin distributes the company wallet); user_id is then the RECIPIENT.';

COMMENT ON TABLE redemption_requests IS
    'Wallet payout legs for BOTH the redemption store (origin=SELF) and the distribution store '
    '(origin=COMPANY_DISTRIBUTION). When origin=COMPANY_DISTRIBUTION, user_id is the RECIPIENT and '
    'wallet_id is the COMPANY wallet the money came from; the initiating partner admin is on '
    'company_distributions.initiated_by_user_id, reachable via '
    'company_distribution_items.redemption_request_id. NOTE: rows with wallet_type=COMPANY and '
    'origin=SELF are retired artifacts of the removed company-redemption endpoint, not distributions.';

-- Supports the origin filters on tenant history, the in-flight cap, and the analytics views.
CREATE INDEX idx_redemption_requests_origin
    ON redemption_requests(client_id, origin);
