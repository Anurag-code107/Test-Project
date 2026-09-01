-- ============================================================
-- Soft delete for client-owned catalog items. Deleted items are hidden from the
-- catalog admin list + seller browse, but the row is retained so historical
-- redemptions (which reference catalog_item_id) still resolve the item name.
-- ============================================================
ALTER TABLE redemption_catalog_items ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- Partial index for the common "active, non-deleted, owned" catalog reads.
CREATE INDEX IF NOT EXISTS idx_redemption_catalog_items_owner_active_not_deleted
    ON redemption_catalog_items (owner_client_id)
    WHERE deleted = FALSE;
