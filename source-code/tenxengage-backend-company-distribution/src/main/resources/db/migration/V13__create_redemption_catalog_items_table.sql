CREATE TABLE redemption_catalog_items (
    id                              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    name                            VARCHAR(255)  NOT NULL,
    description                     VARCHAR(2000) NULL,
    category                        VARCHAR(20)   NOT NULL CHECK (category IN ('CASH', 'NON_CASH')),
    currency_id                     VARCHAR(50)   NOT NULL,
    default_min_redemption_amount   DECIMAL(18,2) NOT NULL CHECK (default_min_redemption_amount > 0),
    default_processing_mode         VARCHAR(30)   NOT NULL DEFAULT 'INSTANT',
    geographic_scope                TEXT[]        NOT NULL DEFAULT '{}',
    provider_item_id                VARCHAR(255)  NULL,
    is_returnable                   BOOLEAN       NOT NULL DEFAULT false,
    default_return_window_days      INT           NOT NULL DEFAULT 0,
    is_active                       BOOLEAN       NOT NULL DEFAULT true,
    xoxoday_last_synced_at          TIMESTAMPTZ   NULL,
    sync_metadata                   JSONB         NULL,
    CONSTRAINT chk_cash_not_returnable
        CHECK (category <> 'CASH' OR is_returnable = false)
);

CREATE INDEX idx_redemption_catalog_items_category
    ON redemption_catalog_items(category, is_active);
CREATE INDEX idx_redemption_catalog_items_currency
    ON redemption_catalog_items(currency_id, is_active);
CREATE UNIQUE INDEX uq_redemption_catalog_items_provider
    ON redemption_catalog_items(category, provider_item_id)
    WHERE provider_item_id IS NOT NULL;
