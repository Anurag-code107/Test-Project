-- Vendor brand image for a catalog item, stamped from the XTRM gift-card SKU at create time
-- (alongside value_type and the amount bounds — see V46).
--
-- Display precedence for a catalog card: client-uploaded image_url → provider_image_url →
-- category-themed inline SVG illustration. Null for NON_CASH (Xoxoday ids are not looked up in the
-- XTRM catalog), for manually-entered SKUs the catalog doesn't surface, and for items created before
-- this column existed — all of which fall back to the illustration exactly as they do today.
ALTER TABLE redemption_catalog_items
    ADD COLUMN provider_image_url VARCHAR(2000);

COMMENT ON COLUMN redemption_catalog_items.provider_image_url IS
    'Brand image URL from the vendor gift-card SKU (XTRM brandImageUrl). Fallback for cards with no uploaded image.';
