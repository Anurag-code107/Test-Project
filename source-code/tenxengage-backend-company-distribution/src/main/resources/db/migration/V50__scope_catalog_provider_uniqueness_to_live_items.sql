-- Align the providerItemId (SKU) unique index with the application rule it is supposed to back.
--
-- RedemptionCatalogAdminService reserves a SKU only for a LIVE card — active, not deleted — and only
-- within its owning client (see the create/activate checks). V13's index predates all three of those
-- rules: UNIQUE (category, provider_item_id) over every row with a non-null SKU. That meant:
--
--   * Deactivating a card did NOT free its SKU. The service check passed (the rival is inactive) and
--     the INSERT then died on this index — a 409 the admin could not clear by any action in the UI.
--   * A soft-deleted card kept its SKU reserved forever, with no row visible anywhere to explain it.
--   * The SKU was reserved GLOBALLY, so one client taking a SKU blocked every other client — wrong
--     under the client-owned catalog model, where two clients may each sell the same gift card.
--
-- Live rows only, owner-scoped. Inactive drafts may share a SKU (new items are created inactive);
-- activation is where a second live card with the same SKU is rejected, and this index is the
-- backstop for two admins activating concurrently.
DROP INDEX IF EXISTS uq_redemption_catalog_items_provider;

CREATE UNIQUE INDEX uq_redemption_catalog_items_provider_live
    ON redemption_catalog_items(owner_client_id, category, provider_item_id)
    WHERE provider_item_id IS NOT NULL AND is_active = true AND deleted = false;
