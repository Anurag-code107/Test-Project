-- Per-client ceiling override, symmetric with min_transaction_amount_override.
-- A client may only NARROW the item's range: the override must sit at or below the item's
-- default_max_redemption_amount (the vendor SKU ceiling) and at or above the effective minimum.
-- NULL = inherit default_max_redemption_amount (which is itself NULL for open-value/legacy items).
ALTER TABLE client_catalog_item_configs
    ADD COLUMN max_transaction_amount_override NUMERIC(18, 2);
