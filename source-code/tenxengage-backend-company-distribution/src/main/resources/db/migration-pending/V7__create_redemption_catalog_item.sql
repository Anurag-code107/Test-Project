CREATE TABLE redemption_catalog_items (
    id                      UUID            NOT NULL DEFAULT uuid_generate_v4(),
    name                    VARCHAR(255)    NOT NULL,
    category                VARCHAR(50)     NOT NULL,
    sub_category            VARCHAR(100),
    currency_type           VARCHAR(50)     NOT NULL,
    minimum_amount          NUMERIC(19, 4)  NOT NULL,
    default_processing_mode VARCHAR(50)     NOT NULL,
    provider_item_id        VARCHAR(255)    NOT NULL,
    geographic_scope        TEXT[],
    is_returnable           BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active               BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_redemption_catalog_items  PRIMARY KEY (id)
);
