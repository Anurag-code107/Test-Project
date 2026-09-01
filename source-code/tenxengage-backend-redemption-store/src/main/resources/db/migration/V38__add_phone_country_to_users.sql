-- Payee mobile country (ISO2) stored alongside the national number in users.phone, so XTRM CreateUser
-- (MobilePhone = dial code + national number) and UpdateUser (MobileCountryISO2 + MobileNumber) can both be
-- built. Nullable for backward-compat with existing rows; enforced going forward at the API layer.
ALTER TABLE users ADD COLUMN phone_country_iso2 VARCHAR(2);
