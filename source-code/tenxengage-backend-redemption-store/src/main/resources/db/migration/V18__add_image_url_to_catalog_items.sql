-- V18__add_image_url_to_catalog_items.sql
ALTER TABLE redemption_catalog_items
    ADD COLUMN image_url VARCHAR(2000) NULL;
