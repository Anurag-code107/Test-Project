-- ============================================================
-- Client-owned catalog (Model 2): every redemption catalog item belongs to exactly
-- ONE client. There is no shared/global catalog. A client sees only its own items.
-- ============================================================

-- 1. Add the owner column (nullable first so existing rows can be backfilled).
ALTER TABLE redemption_catalog_items ADD COLUMN owner_client_id UUID;

-- 2. Backfill pre-existing (previously "global") items to the demo/genicommunity client
--    (a0000000-0000-0000-0000-000000000001 — confirmed = pushpendra@genicommunity.com's client).
UPDATE redemption_catalog_items
SET owner_client_id = 'a0000000-0000-0000-0000-000000000001'
WHERE owner_client_id IS NULL;

-- 3. Enforce ownership going forward.
ALTER TABLE redemption_catalog_items ALTER COLUMN owner_client_id SET NOT NULL;

-- 4. Index the owner — every catalog read is now scoped by it.
CREATE INDEX IF NOT EXISTS idx_redemption_catalog_items_owner_client_id
    ON redemption_catalog_items (owner_client_id);
