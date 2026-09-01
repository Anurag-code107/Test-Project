# Balance Breakage Report DTOs

Read-model DTOs returned by the balance expiration breakage and preview endpoints
(reward-balance-expiration / F-09) at `/api/v1/redemption/expiration/**`. All responses are pure
aggregate projections — the breakage report is computed over `LedgerEntry` rows where
`entry_type = EXPIRY`; the preview aggregates scheduled `BalanceExpiryNotice` events. **No new
JPA entity backs these DTOs, and no per-wallet identity appears in any field.**

`clientId` is never accepted or returned; tenant isolation is resolved server-side from the JWT.

---

## ExpiringBalancePreviewResponse (FR-09.4)

`GET /expiring-soon`. Aggregate preview of balances scheduled to expire within a lead window, one
entry per currency type. Aggregate-only — no per-wallet identity.

| Field | Type | Notes |
|---|---|---|
| `currencyId` | String | Currency code ("cash", "points", "credits", "tickets") |
| `currencyDisplayName` | String | Human-readable label (fallback; FE source of truth is `config/currencies.ts`) |
| `scheduledExpiryDate` | LocalDate | Date these balances are scheduled to expire |
| `affectedWalletCount` | Long | Number of wallets with balance scheduled to expire on this date |
| `totalAmountAtRisk` | BigDecimal | Total amount scheduled to expire (string representation in JSON) |

---

## BalanceBreakageReportResponse (FR-09.6)

`GET /breakage` (JSON) and `GET /breakage/export` (CSV). Breakage (expired value) aggregated from
`EXPIRY` ledger entries, bucketed by `granularity` and broken down by currency type. The CSV
export columns are `period_start,period_end,currency_id,expired_count,total_expired_amount`; each
successful export emits an audit record (`DATA_EXPORTED` / `BALANCE_EXPIRY_BREAKAGE_EXPORT`) and
is rate-limited to 3 req/min per tenant. The date range is required and capped at 24 months.

`from(from, to, granularity, List<BreakageRowDto>)`.

| Field | Type | Notes |
|---|---|---|
| `from` | LocalDate | Start of the reporting window (inclusive) |
| `to` | LocalDate | End of the reporting window (inclusive) |
| `granularity` | String (enum) | `MONTH` \| `QUARTER` — period bucketing applied |
| `rows` | List\<BreakageRowDto\> | One entry per (period × currency type) with at least one expiry event |

### BreakageRowDto

| Field | Type | Notes |
|---|---|---|
| `periodStart` | LocalDate | Start of the period bucket |
| `periodEnd` | LocalDate | End of the period bucket |
| `currencyId` | String | Currency code |
| `currencyDisplayName` | String | Human-readable label (fallback; FE source of truth is `config/currencies.ts`) |
| `expiredCount` | Long | Number of expiry events in this period for this currency type |
| `totalExpiredAmount` | BigDecimal | Total expired amount for this period and currency (string representation in JSON) |

---

## Permissions

| Endpoint | Permission |
|---|---|
| `GET /expiring-soon` | `action.redemption.expiration.configure` |
| `GET /breakage` | `action.redemption.expiration.view_breakage` |
| `GET /breakage/export` | `action.redemption.expiration.view_breakage` |

Both keys are CLIENT_ADMIN-only and the feature is gated by the `reward_balance_expiration`
feature flag (checked in the service layer; 403 if disabled for the tenant's subscription tier).

## Fields Never Exposed in API

| Field | Reason |
|---|---|
| `clientId` | Tenant isolation — resolved server-side from JWT |
| `walletId` / any per-user identifier | Aggregate-only — breakage and preview expose counts + summed amounts per currency/period |

## Data Sources

| Source | Usage |
|---|---|
| `ledger_entries` where `entry_type = EXPIRY` | BalanceBreakageReportResponse (FR-09.6) |
| `balance_expiry_notices` (scheduled events) + `reward_wallets` | ExpiringBalancePreviewResponse (FR-09.4) |
