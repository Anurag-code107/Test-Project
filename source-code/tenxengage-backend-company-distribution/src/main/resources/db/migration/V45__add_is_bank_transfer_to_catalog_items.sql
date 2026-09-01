-- ============================================================
-- Bank-transfer catalog card (redemption-store-feedback). Marks the reserved,
-- hidden, per-client "Bank Transfer" catalog item: excluded from the seller
-- browse and the client-admin catalog list, redeemed only via the dedicated
-- POST /redemption/requests/bank-transfer endpoint. Regular catalog items and
-- gift cards keep is_bank_transfer = FALSE.
-- ============================================================
ALTER TABLE redemption_catalog_items
    ADD COLUMN is_bank_transfer BOOLEAN NOT NULL DEFAULT FALSE;

-- At most one live bank-transfer card per client (backs the idempotent
-- ensureBankTransferCard get-or-create). Partial so it only constrains
-- bank-transfer rows and ignores soft-deleted ones.
CREATE UNIQUE INDEX uq_catalog_bank_transfer_per_client
    ON redemption_catalog_items (owner_client_id)
    WHERE is_bank_transfer = TRUE AND deleted = FALSE;
