# RedemptionAnalyticsSummaryResponse

Top-level analytics summary DTO returned by `GET /api/v1/redemption/analytics`. Aggregates four metric groups from `RewardWallet`, `LedgerEntry`, and `RedemptionRequest` — all scoped to the authenticated tenant. No new database tables are introduced; this is a pure read model. Served from Redis cache (TTL 60 s, keyed `{clientId}:{dateFrom}:{dateTo}`).

## Fields

| Field | Type | Required | Notes |
|---|---|---|---|
| `dateWindow` | DateWindowDto | Yes | Effective date window applied to windowed metrics |
| `redemptionRates` | List\<CurrencyTypeRateDto\> | Yes | Lifetime redemption rate per active currency type; unaffected by date filter |
| `unredeemedBalances` | List\<CurrencyTypeBalanceDto\> | Yes | Unredeemed balance liability snapshot per active currency type; unaffected by date filter |
| `failedCancelledRates` | List\<CurrencyTypeRateDto\> | Yes | Failed/cancelled redemption rate per active currency type within date window |
| `totalRedemptionCount` | RedemptionCountDto | Yes | Total redemption count with status breakdown within date window |

Arrays contain only entries for currency types with at least one `RewardWallet` in the tenant; empty arrays indicate no program activity ("No program activity yet" empty state per FR-07.8).

---

## Nested DTOs

### DateWindowDto

Echoes the effective date window applied to windowed metrics.

| Field | Type | Notes |
|---|---|---|
| `from` | LocalDate | Start of the selected window (inclusive) |
| `to` | LocalDate | End of the selected window (inclusive) |

### CurrencyTypeRateDto

Rate card for a single currency type. Reused for both redemption rate cards and failed/cancelled rate cards.

| Field | Type | Notes |
|---|---|---|
| `currencyId` | String | Platform currency identifier — one of `"CASH"`, `"POINTS"`, `"CREDITS"`, `"TICKETS"` |
| `numerator` | Long | Absolute numerator (total redeemed amount for rate cards; count of FAILED+CANCELLED requests for failed/cancelled cards) |
| `denominator` | Long | Absolute denominator (total earned amount for rate cards; total request count in window for failed/cancelled cards) |
| `ratePercentage` | BigDecimal | Percentage to 2 decimal places, HALF_UP rounding (e.g. `34.25`). Omitted when `hasActivity = false`. |
| `hasActivity` | boolean | `false` when `denominator = 0`; triggers "No redemptions in this period" empty state (FR-07.8) |

### CurrencyTypeBalanceDto

Unredeemed balance liability snapshot for a single currency type.

| Field | Type | Notes |
|---|---|---|
| `currencyId` | String | Platform currency identifier — one of `"CASH"`, `"POINTS"`, `"CREDITS"`, `"TICKETS"` |
| `availableBalance` | Long | Sum of `availableBalance` across all `RewardWallet` rows in tenant for this currency |
| `reservedBalance` | Long | Sum of `reservedBalance` across all `RewardWallet` rows in tenant for this currency |
| `totalOutstanding` | Long | `availableBalance + reservedBalance`; primary card display value |

### RedemptionCountDto

Total redemption count card with status-level breakdown for the selected date window.

| Field | Type | Notes |
|---|---|---|
| `total` | Long | Total count of all `RedemptionRequest` rows in the date window |
| `byStatus` | Map\<String, Long\> | Count per aggregated status key within the date window. Keys: `PENDING` (aggregates PENDING_APPROVAL + RESERVED), `PROCESSING`, `COMPLETED`, `FAILED`, `CANCELLED` — 5 keys total (FR-07.7) |
| `hasActivity` | boolean | `false` when `total = 0`; triggers "No redemptions in this period" empty state (FR-07.8) |

---

## Business Rules

- All queries filtered by `clientId` resolved from `TenantContext.getCurrentClientId()` (JWT) — never from request parameters.
- **Redemption rate (lifetime):** `SUM(LedgerEntry.amount WHERE entryType=REWARD, lifetime)` as denominator; `SUM(LedgerEntry.amount WHERE entryType=REDEMPTION, lifetime)` as numerator; per `currencyId`.
- **Unredeemed balance (snapshot):** `SUM(availableBalance + reservedBalance)` on `RewardWallet`, per `currencyId`. Snapshot — not affected by date filter.
- **Failed/cancelled rate (windowed):** `COUNT(status IN [FAILED, CANCELLED] AND submittedAt IN window)` / `COUNT(submittedAt IN window)` per `currencyId`. Date window applied as UTC instant range.
- `ratePercentage` calculated with `BigDecimal` HALF_UP rounding to 2 decimal places.
- When `denominator = 0`, `hasActivity = false` and `ratePercentage` is omitted. Empty-state message differs by card type (FR-07.8): lifetime rate cards (`redemptionRates`) render "No program activity yet"; windowed rate cards (`failedCancelledRates`) render "No redemptions in this period".
- Cards shown only for `currencyId` values with at least one `RewardWallet` row in the tenant.

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side from JWT; never accepted or returned |

## Data Sources

| Source Entity | Usage |
|---|---|
| `RewardWallet` | `unredeemedBalances` and wallet counts per currency type |
| `LedgerEntry` | `redemptionRates` numerator (REDEMPTION entries) and denominator (REWARD entries) |
| `RedemptionRequest` | `failedCancelledRates` and `totalRedemptionCount` |
