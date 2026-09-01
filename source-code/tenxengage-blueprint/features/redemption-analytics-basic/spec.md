---
slug: redemption-analytics-basic
name: Basic Redemption Analytics Dashboard
status: reviewed
format: story-sliced
roadmap: redemption-store
domain: null
builder_type: null
created: 2026-06-17
contract: null
visual_reference:
  component_path: null
  notes: null
applicable_sections:
  source: null
  sections: []
---

> **Reviewed**: 2026-06-17

# Feature: Basic Redemption Analytics Dashboard

> **Format:** story-sliced
> **Stories, tasks, and per-story tests live in sibling files:**
> - [`stories.md`](stories.md) — story index + dependency graph
> - [`stories/`](stories/) — one `US-NN-*.md` per story (self-contained execution unit)
> - [`tasks/foundation.md`](tasks/foundation.md) — horizontal bedrock tasks
> - [`tracker.md`](tracker.md) — session status tracker
> - [`test-plan.md`](test-plan.md) — cross-story integration tests
>
> **This file is the design reference.** Implementers read it alongside their story file.
>
> **Technical artifacts** (Flyway SQL, file paths, hook specs): see [`technical.md`](technical.md).

---

## Overview

Client Admins gain a health dashboard for their rewards program. The dashboard surfaces four primary metric cards — redemption rate, unredeemed balance liability, failed/cancelled rate, and total redemption count — all scoped to the authenticated admin's tenant. It enables program managers to identify whether earned balances are converting to redemptions, where liability is accumulating, and where vendor friction is causing failures. The unredeemed balance report is exportable as CSV with a per-user and per-company breakdown to support finance team reporting.

This feature satisfies a Phase 1 exit gate requirement per the BRD (§18 acceptance criteria: "Basic redemption analytics dashboard"). It reads exclusively from existing entities (`RewardWallet`, `LedgerEntry`, `RedemptionRequest`) and introduces no new database tables. Advanced analytics (tier/region breakdowns, per-item analysis, cohort comparisons) are deferred to Phase 2 (F-08).

> **Post-launch UX update (2026-06-30 · FE UX enhancements CR-02):** the **Overview** tab no longer renders every currency at once. A **currency dropdown** selects one currency and shows its three metric cards (redemption rate, outstanding liability, failed/cancelled) together; **Total Redemptions** stays global/currency-agnostic. Selection is URL-persisted (`?currency=`); default is the first active currency in priority order. Presentation-only, client-side filter — the summary endpoint already returns per-currency arrays, so **no API/contract change**. CSV export remains all-currency (relabeled "Export all balances"). The Advanced tab (F-08) is unaffected.

### Naming reconciliation

Reconciled against `roadmaps/redemption-store/digest-annex.md` (advisory) vs. actual codebase:

| BRD / advisory name | Codebase name | Decision |
|---|---|---|
| `RedemptionTransaction` | `RedemptionRequest` | Codebase wins — `RedemptionRequest` is the entity name used by F-03 and all existing controllers/services. |

---

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-07.1 | CLIENT_ADMIN can view the redemption rate per currency type: (total amount redeemed ÷ total amount ever earned) × 100%, using a lifetime denominator — not affected by the date filter. Each card shows the absolute earned and redeemed amounts alongside the percentage. |
| FR-07.2 | CLIENT_ADMIN can view the unredeemed balance liability per currency type: the tenant-wide sum of `availableBalance + reservedBalance` across all wallets — a snapshot figure not affected by the date filter. |
| FR-07.3 | CLIENT_ADMIN can view the failed and cancelled redemption rate per currency type within the selected date window: (count of FAILED + CANCELLED `RedemptionRequest` rows) ÷ (total `RedemptionRequest` rows in window) × 100%. |
| FR-07.4 | Dashboard provides a date range filter with presets: Last 7 days, Last 30 days (default on page load), Last 90 days, Last 12 months, and a custom range calendar picker (maximum 24-month span). The filter applies to FR-07.3 and FR-07.7 only; it does NOT affect FR-07.1 or FR-07.2. |
| FR-07.5 | Dashboard reflects data in near real-time: all analytics queries are backed by a Redis cache with a maximum TTL of 60 seconds, keyed per `{clientId}:{dateFrom}:{dateTo}`. |
| FR-07.6 | The unredeemed balance CSV export contains one row per user and one row per partner company in the tenant, with columns: `userId`, `userName`, `companyId`, `companyName`, `currencyType`, `availableBalance`, `reservedBalance`. |
| FR-07.7 | Dashboard displays a total redemption count card for the selected date window: total count of all `RedemptionRequest` rows and a breakdown by status (PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED). Affected by the date filter. |
| FR-07.8 | When the selected date window contains zero redemption activity, windowed metric cards (FR-07.3, FR-07.7) display a "No redemptions in this period" indicator rather than 0%/zero count; when the tenant has no wallets at all, all cards display a "No program activity yet" empty state. |
| FR-07.9 | The CSV export is delivered synchronously as a direct file download (`Content-Disposition: attachment`). If the export rate limit (3 req/min/tenant) is exceeded, the API returns HTTP 429 with `Retry-After` header; the FE export button is disabled with a countdown until re-enabled. |

---

## Functional Completeness Audit

| # | Dimension | Status | FR / Notes |
|---|---|---|---|
| 1 | Redemption rate denominator (lifetime vs. period-specific) | ⊕ Approved | FR-07.1 — Lifetime denominator chosen. Redemptions lag earnings by days/weeks; period denominator produces misleading rates in recent windows. |
| 2 | Dashboard layout (new page vs. tab within existing reporting) | ⊕ Approved | Frontend Specification — New standalone page at `/redemption/admin/analytics`. No existing "reporting page" found in the Client Admin area; nearest pages are `/redemption/admin/history` and `/redemption/approval-queue`. |
| 3 | Date filter presets and default | ⊕ Approved | FR-07.4 — Last 30 days as default; presets for 7d, 30d, 90d, 12mo, custom (max 24 months). |
| 4 | Total redemption count card | ⊕ Approved | FR-07.7 — 4th metric card with status-level breakdown (PENDING / PROCESSING / COMPLETED / FAILED / CANCELLED). |
| 5 | Empty state and zero-activity behavior | ⊕ Approved | FR-07.8 — "No redemptions in this period" for windowed cards; "No program activity yet" for brand-new tenants. |
| 6 | Export delivery model (synchronous vs. async job) | ⊕ Approved | FR-07.9 — Synchronous only. Dataset is small (one row per user/company); background job model used for transaction history (F-05) is not warranted. |
| 7 | Export permission (new permission vs. reuse existing) | ⊕ Approved | Permissions & Feature Flags — No new export permission. `action.redemption.view_analytics` implies export. `action.redemption.export` (also held by PARTNER_ADMIN + PARTNER_SELLER) must NOT grant analytics access. |
| 8 | Near real-time definition | ⊕ Approved | FR-07.5 — ≤60s Redis cache TTL. No Kafka consumer. Query-on-demand acceptable for Phase 1 load (1–10 concurrent admins per tenant). |
| 9 | Scaling risk | ⚠️ FUNCTIONAL GAP — DEFERRED | FR-07.5 NFR note — Query-on-demand against `LedgerEntry` may require materialized views if tenant scale exceeds ~50k users or ~5M entries. Explicitly deferred to Phase 2 (F-08) for evaluation. |
| 10 | Currency types with zero activity | ✓ Already covered | FR-07.1–07.3 — Metric shown "per currency type with active wallets." FR-07.8 — Zero-activity handled by `hasActivity` flag in response DTO. |

---

## Non-Functional Requirements

| Dimension | Requirement | Notes |
|---|---|---|
| **Response time (analytics read)** | P95 < 500ms | Served from Redis on cache-hit. Cold query may exceed 500ms on large tenants — see scaling risk. |
| **Response time (export)** | Best-effort | Rate-limited to protect the DB; not latency-critical. |
| **Peak concurrent admins** | 1–10 per tenant | Query-on-demand acceptable at this scale. |
| **Availability** | 99.5% | Inherits platform SLA. |
| **Cache TTL** | ≤60 seconds | Redis; per-tenant, per-date-window. |
| **Data sensitivity** | CONFIDENTIAL + PII | Aggregates = CONFIDENTIAL. Export CSV contains user/company names = PII. |
| **Compliance** | GDPR | Export PII fields subject to data subject access/deletion requests. |
| **Audit retention** | 1 year | Export audit records. |
| **Scaling risk** | Phase 2 gate | Aggregation queries against `LedgerEntry` and `RedemptionRequest` at scale require materialized views. Revisit when tenant user base exceeds ~50k or transaction count exceeds ~5M. |

---

## Prerequisites

- [ ] Spec reviewed via `/review-spec` (status must be `reviewed`)
- [ ] Contracts generated via `/generate-contracts` in the contracts repo
- [ ] Next Flyway migration number confirmed: **V27** (`V27__seed_redemption_analytics_permissions.sql`)
- [ ] F-01 (Wallet & Ledger Foundation) implemented — `RewardWallet` and `LedgerEntry` entities must exist
- [ ] F-03 (Redemption Flow) implemented — `RedemptionRequest` entity must exist
- [ ] Redis available in the deployment environment (already a platform dependency)

---

## New Enums [BE]

No new enums are introduced by this feature. `RedemptionStatus`, `RedemptionCategory`, and `WalletType` exist in the codebase. Note: `CurrencyType.java` has two values (`MONETARY`/`NON_MONETARY`) and is **not** used by this feature. Currency identity is stored as `currencyId: String` on `RewardWallet`, `LedgerEntry`, and `RedemptionRequest` — values are the 4 platform currency identifiers (`"cash"`, `"points"`, `"credits"`, `"tickets"`) as seeded in `V3__baseline_tenant_and_config.sql`.

New `AuditResourceType` enum value required: `REDEMPTION_ANALYTICS_EXPORT`. See Audit Trail section.

---

## Data Model / Entities [BE]

### Entity-shape decisions

F-07 introduces no new database entities. All three source entities (`RewardWallet`, `LedgerEntry`, `RedemptionRequest`) are pre-existing hardcoded JPA entities inherited from the roadmap digest and earlier specs (F-01, F-03, F-05). No entity-shape decisions are required for this feature.

_No entity tables to document. See DTOs section for the response shape produced by this feature._

---

## Permissions & Feature Flags [BE + FE]

### Permission Matrix

| Permission Key | Display Name | Type | Scope | Category | CLIENT_ADMIN | ACTIVITY_APPROVER | PARTNER_ADMIN | PARTNER_SELLER |
|---|---|---|---|---|---|---|---|---|
| `action.redemption.view_analytics` | View Redemption Analytics | ACTION | `INTERNAL` | REDEMPTION_ACTIONS | Y | — | — | — |

**Notes:**
- `action.redemption.view_analytics` is new, introduced by F-07. It gates both the analytics page (FR-07.1–FR-07.8) and the CSV export endpoint (FR-07.9). No separate export permission is introduced.
- `action.redemption.export` (existing, held by CLIENT_ADMIN + PARTNER_ADMIN + PARTNER_SELLER) is **not** used for analytics. It governs transaction history export (F-05) and must not grant analytics dashboard access.
- INTERNAL scope: this permission is only assignable to client-level roles. Partner roles cannot hold it.

### Feature Flag

| Feature Key | Description | starterEnabled | professionalEnabled | enterpriseEnabled | Category |
|---|---|---|---|---|---|
| `redemption_store` | Redemption Store module access | `false` | `true` | `true` | REDEMPTION |

No new feature flag is introduced. F-07 reuses the existing `redemption_store` feature flag.

_Flyway seed SQL for the new permission lives in `technical.md → ## Flyway Migrations [BE]`._

---

## DTOs [BE]

_Path: `src/main/java/com/tenxengage/app/dto/response/redemption/`_

### Response DTOs

**`RedemptionAnalyticsSummaryResponse`** — returned by `GET /api/v1/redemption/analytics`

| Record | Static Factory | Rendered Fields |
|---|---|---|
| `RedemptionAnalyticsSummaryResponse` | `of(dateWindow, redemptionRates, unredeemedBalances, failedCancelledRates, totalRedemptionCount)` | `dateWindow` (DateWindowDto), `redemptionRates` (List\<CurrencyTypeRateDto\>), `unredeemedBalances` (List\<CurrencyTypeBalanceDto\>), `failedCancelledRates` (List\<CurrencyTypeRateDto\>), `totalRedemptionCount` (RedemptionCountDto) |

**`DateWindowDto`** (nested inside `RedemptionAnalyticsSummaryResponse`)

| Field | Type | Rendered As |
|---|---|---|
| `from` | `LocalDate` | Start of selected date window — displayed in the date filter label |
| `to` | `LocalDate` | End of selected date window — displayed in the date filter label |

**`CurrencyTypeRateDto`** (used for both redemption rate cards and failed/cancelled rate cards)

| Field | Type | Rendered As |
|---|---|---|
| `currencyId` | `String` | Card label: "cash Redemption Rate", "points Redemption Rate", etc. (value is one of: `"cash"`, `"points"`, `"credits"`, `"tickets"`) |
| `numerator` | `Long` | Absolute numerator (total redeemed amount; or count of FAILED + CANCELLED requests) |
| `denominator` | `Long` | Absolute denominator (total earned amount; or total requests in window) |
| `ratePercentage` | `String` | Plain decimal string, no `%` suffix — e.g. `"34.25"`; 2 decimal places, HALF_UP rounding; `"0.00"` when `hasActivity = false` |
| `hasActivity` | `boolean` | `false` when denominator = 0 — triggers "No redemptions in this period" empty state (FR-07.8) |

**`CurrencyTypeBalanceDto`** (used for unredeemed balance cards)

| Field | Type | Rendered As |
|---|---|---|
| `currencyId` | `String` | Card label: "cash Outstanding Liability", etc. (value is one of: `"cash"`, `"points"`, `"credits"`, `"tickets"`) |
| `availableBalance` | `Long` | Displayed via `getCurrency()` formatting |
| `reservedBalance` | `Long` | Displayed via `getCurrency()` as sub-label "(N reserved)" |
| `totalOutstanding` | `Long` | Primary card value = `availableBalance + reservedBalance`; formatted via `getCurrency()` |

**`RedemptionCountDto`** (used for total redemption count card)

| Field | Type | Rendered As |
|---|---|---|
| `total` | `Long` | Primary count: "N redemptions" |
| `byStatus` | `Map<String, Long>` | Status-row breakdown: PENDING, PROCESSING, COMPLETED, FAILED, CANCELLED each with count |
| `hasActivity` | `boolean` | `false` when total = 0 — triggers "No redemptions in this period" empty state (FR-07.8) |

### Request DTOs

No request DTOs are required. Both endpoints accept query parameters only — no request body.

### CSV Export columns

The export endpoint (`GET /api/v1/redemption/analytics/export`) streams `text/csv`. Each row represents one wallet record. Columns:

| Column | Type | Source |
|---|---|---|
| `userId` | `UUID` | `RewardWallet.userId` |
| `userName` | `String` | User display name (user lookup by `RewardWallet.userId`) |
| `companyId` | `UUID` or empty | `RewardWallet.partnerCompanyId` (empty string if null — individual wallet) |
| `companyName` | `String` | Partner company name (`"Individual"` if no company association) |
| `currencyType` | `String` | `RewardWallet.currencyId` (e.g., `"cash"`) |
| `availableBalance` | `Long` | `RewardWallet.availableBalance` |
| `reservedBalance` | `Long` | `RewardWallet.reservedBalance` |

**PII fields in export:** `userName`, `companyName`. Classification: CONFIDENTIAL. Export is audited.

---

## API Endpoints [BE + FE]

_Base path: `/api/v1/redemption/analytics`_
_Controller: `RedemptionAnalyticsController`_
_Tag: `Redemption Analytics`_

| Method | Path | Request Body | Response | Status | Permission | Audit |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/redemption/analytics` | — (query params) | `RedemptionAnalyticsSummaryResponse` | 200 | `action.redemption.view_analytics` | — |
| `GET` | `/api/v1/redemption/analytics/export` | — | `text/csv` stream | 200 | `action.redemption.view_analytics` | Programmatic — `auditLogService.logAsync()` in service after CSV built |

**Query parameters for `GET /api/v1/redemption/analytics`:**

| Parameter | Type | Required | Default | Constraints |
|---|---|---|---|---|
| `dateFrom` | `LocalDate` (ISO 8601: YYYY-MM-DD) | No | Today minus 30 days | Must not be after `dateTo` |
| `dateTo` | `LocalDate` (ISO 8601: YYYY-MM-DD) | No | Today | Must not be before `dateFrom`; range ≤ 730 days |

**Error responses (both endpoints):**

| Status | Condition |
|---|---|
| `400 Bad Request` | Invalid date format (non-ISO 8601 string) |
| `401 Unauthorized` | Missing or expired JWT |
| `403 Forbidden` | Caller does not hold `action.redemption.view_analytics` |
| `422 Unprocessable Entity` | `dateFrom` after `dateTo`, or range exceeds 730 days (24 months) |
| `429 Too Many Requests` | Rate limit exceeded; response includes `Retry-After` header |

---

## Service Layer [BE]

_Path: `src/main/java/com/tenxengage/app/service/redemption/RedemptionAnalyticsService.java`_

| Method | Return Type | Notes |
|---|---|---|
| `getAnalyticsSummary(dateFrom, dateTo)` | `RedemptionAnalyticsSummaryResponse` | `@Transactional(readOnly=true)`. Resolves `clientId` from `TenantValidator`. Checks Redis cache first; on miss, runs aggregation queries and populates cache at TTL 60s. |
| `exportUnredeemedBalances()` | `byte[]` | `@Transactional(readOnly=false)`. Resolves `clientId` from `TenantValidator`. Queries all `RewardWallet` rows for tenant, joins user/company names, serializes to CSV. Not cached. |

**Business rules:**

- Redemption rate: `SUM(LedgerEntry.amount WHERE entryType = DEBIT, lifetime) / SUM(LedgerEntry.amount WHERE entryType = CREDIT, lifetime) × 100`, per `currencyId`, filtered by `clientId`.
- Unredeemed balance: `SUM(availableBalance + reservedBalance)` on `RewardWallet` filtered by `clientId`, grouped by `currencyId`.
- Failed/cancelled rate: `COUNT(status IN [FAILED, CANCELLED] AND submittedAt IN window) / COUNT(submittedAt IN window) × 100`, per `currencyId`, filtered by `clientId`. When denominator = 0, `hasActivity = false`. Date window applied as `Instant` range: `dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant()` to `dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()` (exclusive end).
- `ratePercentage` calculated to 2 decimal places using `BigDecimal` with `HALF_UP` rounding.
- Cards shown only for currency types with at least one `RewardWallet` row in the tenant (i.e., `currencyId` values present for the tenant).
- Export: individual wallets (null `companyId`) export `companyId` as empty string, `companyName` as `"Individual"`.

**Tenant isolation contract:** Both service methods resolve `clientId` from `TenantValidator.getCurrentClientId()` (reads `CustomUserDetails` from Spring Security context). Never accepts `clientId` as an API layer parameter.

---

## Security Design [BE]

### Data Classification

| Field / Dataset | Classification | Handling |
|---|---|---|
| Analytics aggregates (rates, counts, balances) | Confidential | INTERNAL scope — CLIENT_ADMIN only. Not exposed to partner roles. |
| Export CSV `userName`, `companyName` | PII | Audited at download. Subject to GDPR data export/deletion requests. Not logged in application logs. |
| `client_id` | Internal | Never returned in API responses. |

### Rate Limiting

| Endpoint | Limit | Scope | Reason |
|---|---|---|---|
| `GET /api/v1/redemption/analytics` | 10 req/min | Per user | Aggregation query — prevents single admin saturating the cache-miss path. |
| `GET /api/v1/redemption/analytics/export` | 3 req/min | Per tenant | Join-heavy export — shared tenant bucket prevents concurrent heavy downloads. |

Analytics summary rate limit (`10 req/min/user`) is implemented via the existing `RateLimitFilter`. Export rate limit (`3 req/min/tenant`) is implemented via `AnalyticsExportRateLimiter` injected directly into `RedemptionAnalyticsController` — NOT via `RateLimitFilter`.

### OWASP Risks & Mitigations

| Risk | Where | Mitigation |
|---|---|---|
| **Broken Access Control (A01)** | Both endpoints | `@RequiresPermission("action.redemption.view_analytics")` — CLIENT_ADMIN only. Hibernate `@Filter` scopes all queries to `clientId`. |
| **Cross-tenant data leak (A01)** | Both endpoints | `clientId` resolved exclusively from `TenantContext` (JWT), never from request params. Queries use parameterized JPQL with `clientId`. |
| **Injection (A03)** | `dateFrom`/`dateTo` params | Spring `LocalDate` binding handles parsing; invalid dates return 400 before service layer. No raw string used in queries. |

### Input Validation Summary

| Field | Constraints | Rejection |
|---|---|---|
| `dateFrom` | ISO 8601 date string; Spring `LocalDate` binding | `400 Bad Request` on parse failure |
| `dateTo` | ISO 8601 date string; Spring `LocalDate` binding | `400 Bad Request` on parse failure |
| `dateFrom`/`dateTo` cross-field | `dateFrom ≤ dateTo`, range ≤ 730 days | `422 Unprocessable Entity` in service layer |

---

## Audit Trail [BE]

_Uses existing `@Audited` infrastructure._

| Operation | Entity | Data Captured | Who Can View |
|---|---|---|---|
| CSV export downloaded | `REDEMPTION_ANALYTICS_EXPORT` | `exportedBy` (userId), `clientId`, `downloadedAt`, `rowCount` | `CLIENT_ADMIN` |

### New Audit Enum Values

| Enum | New Value | Reason |
|---|---|---|
| `AuditResourceType` | `REDEMPTION_ANALYTICS_EXPORT` | New resource type for analytics CSV download audit |

`AuditAction.DATA_EXPORTED` already exists (used by `RedemptionExportController`). No new `AuditAction` value required.

### `@Audited` Annotation Details

| Endpoint | `action` | `resourceType` | `description` | Mechanism |
|---|---|---|---|---|
| `GET /api/v1/redemption/analytics/export` | `DATA_EXPORTED` | `REDEMPTION_ANALYTICS_EXPORT` | `Analytics unredeemed balance export downloaded` | `auditLogService.logAsync()` called in `exportUnredeemedBalances()` after CSV is built — NOT an `@Audited` annotation on the controller |

`GET /api/v1/redemption/analytics` is NOT audited — read-only, non-PII aggregate data.

**Audit record retention:** 1 year.

---

## Observability [BE]

### MDC Fields

| MDC Key | Value | Set By |
|---|---|---|
| `requestId` | UUID from `X-Request-ID` header | `RequestContextFilter` (existing) |
| `tenantId` | `clientId` from JWT | `TenantFilter` (existing) |
| `userId` | User ID from JWT | `JwtAuthenticationFilter` (existing) |
| `featureArea` | `"redemption-analytics"` | Set in `RedemptionAnalyticsService` |

### Key Log Events

| Event | Level | `step` value | Key Fields | Purpose |
|---|---|---|---|---|
| Summary served (cache hit) | INFO | `analytics_summary_cache_hit` | `tenantId`, `dateFrom`, `dateTo` | Cache effectiveness monitoring |
| Summary computed (cache miss) | INFO | `analytics_summary_computed` | `tenantId`, `dateFrom`, `dateTo`, `durationMs` | Cold-query latency tracking |
| Export downloaded | INFO | `analytics_export_downloaded` | `tenantId`, `userId`, `rowCount` | Business event tracking |
| Rate limit exceeded | WARN | `rate_limit_exceeded` | `endpoint`, `userId` or `tenantId` | Abuse detection |

### Metrics

| Metric Name | Type | Labels | Purpose |
|---|---|---|---|
| `redemption.analytics.requests.total` | Counter | `tenantId`, `cacheHit` | Volume + cache-hit-rate tracking |
| `redemption.analytics.export.total` | Counter | `tenantId` | Export volume tracking |
| `redemption.analytics.summary.duration_ms` | Histogram | — | Cold-query latency (alert if P95 > 2000ms — scaling risk signal) |

---

## Frontend Specification [FE]

_TypeScript types live in `../tenxengage-contracts/`. Full FE file paths and hook specs: see `technical.md`._

### Pages

| Page | Route | Layout | Permission | Sidebar Entry |
|---|---|---|---|---|
| `RedemptionAnalyticsPage` | `/redemption/admin/analytics` | `AppLayout` | `action.redemption.view_analytics` | Yes — under "Redemption" section, label "Analytics" |

### Key Components

| Component | Props | Data Source | Notes |
|---|---|---|---|
| `RedemptionAnalyticsPage` | — | `useRedemptionAnalytics(dateFrom, dateTo)` | **Sections:** (1) **Date Filter Bar** — preset buttons (Last 7d / 30d / 90d / 12mo) + Custom range calendar picker via `DateRangeFilter`; selected range held in local state; (2) **Metric Cards Grid** — 4-column desktop / 2-column tablet / 1-column mobile: Redemption Rate cards (`redemptionRates` array, one per currency type), Unredeemed Balance cards (`unredeemedBalances` array), Failed/Cancelled Rate cards (`failedCancelledRates` array), Total Count card (`totalRedemptionCount`); (3) **Export Section** — Export button opens `ExportConfirmDialog`. **Interactions:** preset buttons update date range and trigger refetch; calendar validates max 24-month span before committing; Export button opens confirmation dialog. **A11y & responsive:** per `../tenxengage-frontend/PROJECT-CONTEXT.md`. |
| `RedemptionRateCard` | `data: CurrencyTypeRateDto` | prop | **Sections:** `currencyId` as card label (e.g., "cash"); `ratePercentage` as large display; sub-labels showing `numerator` (total redeemed) and `denominator` (total earned) via `getCurrency()`. Empty state when `hasActivity = false`. |
| `UnredeemedBalanceCard` | `data: CurrencyTypeBalanceDto` | prop | **Sections:** `currencyId` as card label (e.g., "cash"); `totalOutstanding` as primary value via `getCurrency()`; sub-labels for `availableBalance` and `reservedBalance` separately. |
| `FailedCancelledRateCard` | `data: CurrencyTypeRateDto` | prop | Same structure as `RedemptionRateCard`. `currencyId` as label; failed/cancelled count as numerator; total requests as denominator. Empty state when `hasActivity = false`. |
| `TotalCountCard` | `data: RedemptionCountDto` | prop | **Sections:** `total` count (large display); status-breakdown list for PENDING / PROCESSING / COMPLETED / FAILED / CANCELLED with individual counts. Empty state when `hasActivity = false`. |
| `ExportConfirmDialog` | `onConfirm: () => void`, `isLoading: boolean` | — | shadcn `<Dialog>`. **Sections:** title "Export Unredeemed Balances"; `<DialogDescription>` "This will download a CSV file containing balance data for all users and companies in your tenant." (required for ARIA); Confirm and Cancel buttons. On confirm: triggers `useAnalyticsExport()` mutation. |
| `DateRangeFilter` | `value: DateRange`, `onChange: (range: DateRange) => void` | prop | Preset buttons + shadcn `<Popover>` + `<Calendar>` picker. Validates max 24-month span before invoking `onChange`. |

### Data Flow (TanStack Query)

| Hook | Query Key | Endpoint | StaleTime | Invalidation |
|---|---|---|---|---|
| `useRedemptionAnalytics(dateFrom, dateTo)` | `['redemption-analytics', clientId, dateFrom, dateTo]` | `GET /api/v1/redemption/analytics?dateFrom=...&dateTo=...` | 60 s (matches server cache TTL) | Date range change triggers key change → automatic refetch |
| `useAnalyticsExport()` | — (mutation) | `GET /api/v1/redemption/analytics/export` | — | — |

_Full hook specs: see `technical.md → ## Hook Specs [FE]`._

---

## Caching Strategy [BE]

| What | Cache Location | TTL | Cache Key | Invalidation Trigger |
|---|---|---|---|---|
| Analytics summary response | Redis `@Cacheable("redemption-analytics")` | 60 seconds | `{clientId}:{dateFrom}:{dateTo}` | TTL expiry only (analytics data is append-only; eventual consistency is acceptable) |

Export response is NOT cached — always reflects the live snapshot.

---

## Data Retention & Compliance [BE]

### Soft Delete vs Hard Delete

F-07 introduces no new entities; no soft/hard delete decisions required.

### PII Handling

| Field | Source | PII Type | GDPR Treatment |
|---|---|---|---|
| `userName` | User lookup (in export CSV) | Name | On data-subject deletion request: omit from future exports; historical audit records retained but user record anonymized by the user management module. |
| `companyName` | Company lookup (in export CSV) | Organization name | Retained — company name is not personal data under GDPR unless a sole trader. |

### Data Retention Periods

| Data Type | Retention Period | Justification |
|---|---|---|
| Analytics export audit records | 1 year | Internal compliance audit trail |
| PII in Redis cache | ≤60 seconds | Cache TTL — short-lived |

---

## Edge Cases [BE + FE]

1. **Brand-new tenant (no wallets)** — All metric cards display "No program activity yet". API returns empty arrays; `totalRedemptionCount.total = 0`.
2. **Date window with no redemption activity** — `failedCancelledRates` and `totalRedemptionCount` return `hasActivity = false`. FE renders "No redemptions in this period" per FR-07.8. Lifetime cards (FR-07.1, FR-07.2) are unaffected.
3. **Zero earned amount (no earning events yet)** — Redemption rate denominator = 0. `CurrencyTypeRateDto.hasActivity = false` for that currency type. FE shows empty state rather than dividing by zero.
4. **Custom date range exceeds 24 months** — FE calendar picker prevents this client-side. If bypassed, API returns `422 Unprocessable Entity`.
5. **`dateFrom` after `dateTo`** — Service layer returns 422: `"dateFrom must not be after dateTo."`.
6. **Export rate limit** — HTTP 429 with `Retry-After`. FE shows: "Export limit reached. You can export again in {N} seconds." Export button disabled with countdown.
7. **Analytics rate limit** — HTTP 429. FE shows toast: "Too many requests. Please wait before refreshing."
8. **Tenant uses only 2 of 4 currency types** — Only cards for active currency types are rendered. No empty card for inactive types.
9. **Cross-tenant request** — Queries filter by `clientId` from `TenantContext`. Hibernate `@Filter` provides secondary enforcement.
10. **Permission removed mid-session** — Next API request returns 403. Server enforces `@RequiresPermission`; no security leak from stale page state.
11. **Redis unavailable** — Spring `@Cacheable` falls through to direct DB query on cache miss. Latency degrades but availability is maintained.

---

## Acceptance Tests

_Tests are split across two locations:_
- **Per-story tests** (unit, @WebMvcTest, Vitest, E2E Playwright) — live inside each `stories/US-NN-*.md` file alongside the code they verify
- **Cross-story integration tests** (Testcontainers, multi-entity workflows, tenant isolation) — in [test-plan.md](test-plan.md)

---

## Out of Scope

- **Tier/region analytics** (Phase 2 — F-08): explicitly deferred per BRD v1 acceptance criteria.
- **Per-catalog-item breakdown** (Phase 2 — F-08): which items are redeemed most/least.
- **Cohort analysis** (Phase 2): comparing behavior across user cohorts.
- **Real-time analytics** (Phase 3): sub-second freshness; requires dedicated event stream pipeline.
- **Background export jobs**: export delivers synchronously. No `RedemptionAnalyticsExportJob` entity introduced.
- **Cross-tenant analytics**: no platform-admin view aggregating across all tenants in Phase 1.
- **Materialized views / dedicated analytics schema**: deferred to Phase 2 evaluation.

---

## Verification Steps

### Backend Verification

1. `./gradlew bootRun` — application starts; Flyway V27 migration applies without errors
2. `./gradlew test` — all new and existing tests pass; JaCoCo 60%/50% minimums met
3. Security spot-checks:
   - `GET /api/v1/redemption/analytics` with CLIENT_ADMIN JWT from Tenant A → 200 with Tenant A data only
   - Same endpoint with CLIENT_ADMIN JWT from Tenant B → 200 with Tenant B data only (isolation confirmed)
   - Same endpoint with PARTNER_SELLER JWT → 403 Forbidden
   - No JWT → 401 Unauthorized
4. Rate limit check: 11 consecutive analytics requests within 60s → 11th returns 429
5. Export rate limit: 4 export requests within 60s from same tenant → 4th returns 429 with `Retry-After` header

### Frontend Verification

1. `npm run build` — no TypeScript errors
2. `npm run test` — Vitest passes
3. UI smoke checks:
   - Navigate to `/redemption/admin/analytics` as CLIENT_ADMIN → page loads with metric cards
   - Switch date filter from "Last 30 days" to "Last 7 days" → cards refresh
   - Select custom date range > 24 months → calendar picker prevents the selection
   - Click Export → confirmation dialog opens with `DialogDescription` text visible
   - Confirm export → CSV file downloads
   - Navigate to `/redemption/admin/analytics` as PARTNER_SELLER → redirect (ProtectedRoute)

---

## Planning seeds (from feature brief)

_Copied verbatim from `roadmaps/redemption-store/features/F-07-redemption-store.md` § Suggested story seeds._

| # | Title | Business outcome | Type | Depends on |
|---|---|---|---|---|
| S-01 | View redemption rate by currency | Client Admin sees the percentage of earned balances that have been redeemed per currency type | reporting | F-03.S-06 |
| S-02 | View unredeemed balance liability | Client Admin sees and exports the total outstanding unredeemed balance across all tenant wallets by currency type | reporting | F-01.S-01 |
| S-03 | View failed and cancelled redemption rates | Client Admin identifies fulfillment friction by monitoring failure and cancellation rates | reporting | S-01 |
| S-04 | Filter analytics by date range | Client Admin analyzes redemption health across specific time windows | UI | S-01 |

**FR deviation note:** The brief defines FR-07.1–FR-07.6. This spec adds FR-07.7 (total count card with status breakdown), FR-07.8 (empty states), and FR-07.9 (synchronous export + rate limit UX) — all originated from the functional completeness probe (step 01a). These are appended net-new requirements, not renumberings of existing brief FRs.
