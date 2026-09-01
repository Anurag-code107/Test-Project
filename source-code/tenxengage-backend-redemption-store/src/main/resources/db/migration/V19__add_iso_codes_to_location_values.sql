-- V19: Add ISO 3166-1 alpha-2 codes to country location values
-- Applies to all tenants; only updates rows where code is currently NULL.

-- AMERICAS
UPDATE location_values SET code = 'US' WHERE name = 'United States' AND code IS NULL;
UPDATE location_values SET code = 'CA' WHERE name = 'Canada'        AND code IS NULL;

-- LATAM
UPDATE location_values SET code = 'MX' WHERE name = 'Mexico'    AND code IS NULL;
UPDATE location_values SET code = 'BR' WHERE name = 'Brazil'    AND code IS NULL;
UPDATE location_values SET code = 'AR' WHERE name = 'Argentina' AND code IS NULL;
UPDATE location_values SET code = 'CL' WHERE name = 'Chile'     AND code IS NULL;
UPDATE location_values SET code = 'CO' WHERE name = 'Colombia'  AND code IS NULL;
UPDATE location_values SET code = 'PE' WHERE name = 'Peru'      AND code IS NULL;

-- EMEAR
UPDATE location_values SET code = 'GB' WHERE name = 'United Kingdom' AND code IS NULL;
UPDATE location_values SET code = 'DE' WHERE name = 'Germany'        AND code IS NULL;
UPDATE location_values SET code = 'FR' WHERE name = 'France'         AND code IS NULL;
UPDATE location_values SET code = 'ES' WHERE name = 'Spain'          AND code IS NULL;
UPDATE location_values SET code = 'IT' WHERE name = 'Italy'          AND code IS NULL;
UPDATE location_values SET code = 'NL' WHERE name = 'Netherlands'    AND code IS NULL;
UPDATE location_values SET code = 'SE' WHERE name = 'Sweden'         AND code IS NULL;
UPDATE location_values SET code = 'NO' WHERE name = 'Norway'         AND code IS NULL;
UPDATE location_values SET code = 'DK' WHERE name = 'Denmark'        AND code IS NULL;
UPDATE location_values SET code = 'FI' WHERE name = 'Finland'        AND code IS NULL;
UPDATE location_values SET code = 'PL' WHERE name = 'Poland'         AND code IS NULL;
UPDATE location_values SET code = 'IE' WHERE name = 'Ireland'        AND code IS NULL;
UPDATE location_values SET code = 'BE' WHERE name = 'Belgium'        AND code IS NULL;
UPDATE location_values SET code = 'CH' WHERE name = 'Switzerland'    AND code IS NULL;
UPDATE location_values SET code = 'AT' WHERE name = 'Austria'        AND code IS NULL;
UPDATE location_values SET code = 'PT' WHERE name = 'Portugal'       AND code IS NULL;
UPDATE location_values SET code = 'CZ' WHERE name = 'Czech Republic' AND code IS NULL;
UPDATE location_values SET code = 'RO' WHERE name = 'Romania'        AND code IS NULL;
UPDATE location_values SET code = 'ZA' WHERE name = 'South Africa'   AND code IS NULL;
UPDATE location_values SET code = 'NG' WHERE name = 'Nigeria'        AND code IS NULL;
UPDATE location_values SET code = 'KE' WHERE name = 'Kenya'          AND code IS NULL;
UPDATE location_values SET code = 'AE' WHERE name = 'UAE'            AND code IS NULL;
UPDATE location_values SET code = 'SA' WHERE name = 'Saudi Arabia'   AND code IS NULL;
UPDATE location_values SET code = 'IL' WHERE name = 'Israel'         AND code IS NULL;
UPDATE location_values SET code = 'TR' WHERE name = 'Turkey'         AND code IS NULL;

-- APJ
UPDATE location_values SET code = 'AU' WHERE name = 'Australia'   AND code IS NULL;
UPDATE location_values SET code = 'JP' WHERE name = 'Japan'        AND code IS NULL;
UPDATE location_values SET code = 'CN' WHERE name = 'China'        AND code IS NULL;
UPDATE location_values SET code = 'IN' WHERE name = 'India'        AND code IS NULL;
UPDATE location_values SET code = 'KR' WHERE name = 'South Korea'  AND code IS NULL;
UPDATE location_values SET code = 'SG' WHERE name = 'Singapore'    AND code IS NULL;
UPDATE location_values SET code = 'NZ' WHERE name = 'New Zealand'  AND code IS NULL;
UPDATE location_values SET code = 'ID' WHERE name = 'Indonesia'    AND code IS NULL;
UPDATE location_values SET code = 'MY' WHERE name = 'Malaysia'     AND code IS NULL;
UPDATE location_values SET code = 'TH' WHERE name = 'Thailand'     AND code IS NULL;
UPDATE location_values SET code = 'PH' WHERE name = 'Philippines'  AND code IS NULL;
UPDATE location_values SET code = 'VN' WHERE name = 'Vietnam'      AND code IS NULL;
UPDATE location_values SET code = 'TW' WHERE name = 'Taiwan'       AND code IS NULL;
UPDATE location_values SET code = 'HK' WHERE name = 'Hong Kong'    AND code IS NULL;
