-- Gift-card value type + upper bound for catalog items.
-- FIXED_VALUE XTRM SKUs are a single denomination (min == max == faceValue); VARIABLE_VALUE SKUs accept
-- any amount within [minValue, maxValue]. Both nullable: legacy items and the reserved bank-transfer card
-- have no value_type (treated as open-value with the existing min floor and no ceiling).
ALTER TABLE redemption_catalog_items
    ADD COLUMN value_type VARCHAR(20),
    ADD COLUMN default_max_redemption_amount NUMERIC(18, 2);
