# Pattern: currency-handling

## When this applies

Feature involves money — pricing, transactions, billing amounts, payouts, refunds, commissions.

## Spec authoring guidance

- **Storage type:** the spec MUST specify amounts as `BIGINT` (storing minor units, e.g., cents) — never `DOUBLE` or `FLOAT`. Document this in the Data Model section's column types.
- **Currency code field:** every monetary amount column needs a paired `currency_code` `VARCHAR(3)` column (ISO 4217). Do not assume a single currency; the platform supports multiple.
- **No floating-point arithmetic** anywhere on the path between API and DB. Spec must call out: "Service layer uses `long` (minor units) for all arithmetic; conversion to/from major units happens only at API boundary."
- **Display vs storage rounding:** spec the rounding policy explicitly (typically `HALF_UP` for display, no rounding in storage). Reference any centralized rounding utility.
- **Valid built-in currency IDs in this platform:** `cash`, `points`, `credits`, `tickets` (from `src/config/currencies.ts`). Spec must use only these values for built-in currencies. Custom currencies are supported via API hydration — spec must reference `hydrateCurrencies()` for any feature that surfaces currency selection dynamically.

## Implementation guidance

- Use `long` for all in-process amount arithmetic.
- Use `java.math.BigDecimal` ONLY when interacting with external systems that require it; convert at the boundary.
- Frontend: use `getCurrency(id)` from `src/config/currencies.ts`. Display formatting uses the config's `format` / `rewardFormat` functions (which wrap `Intl.NumberFormat` with the correct locale). Never use `parseFloat` on currency input; parse into minor units with proper rounding.
- For budget/admin contexts use the `format` function; for reward-facing contexts (balance cards, transaction history) use `rewardFormat`.
- Never use `gift_card` or `training_credit` — these are legacy IDs and must not be referenced in new features.

## Examples in codebase

- Currency config: `tenxengage-frontend/src/config/currencies.ts`
- Existing money-handling fields: `grep -rn "BIGINT\|currency_code" tenxengage-backend/src/main/resources/db/migration/`

## Common gotchas

- **Locale-dependent formatting.** Some locales use `,` as decimal separator. Don't string-parse user input; use proper Intl APIs via `getCurrency().format()`.
- **Currencies with non-2 decimal places.** JPY has 0 decimal places, BHD has 3. Don't hardcode `* 100` in conversion logic.
- **DB precision drift.** `NUMERIC(10, 2)` loses precision vs `BIGINT` for minor units. Stick with `BIGINT`.
- **Using `gift_card` or `training_credit`.** These are deprecated currency IDs. Never reference them in new features.
- **Bypassing `getCurrency()` for display.** Always route through the config's format functions — they handle monetary vs non-monetary display differences automatically.
