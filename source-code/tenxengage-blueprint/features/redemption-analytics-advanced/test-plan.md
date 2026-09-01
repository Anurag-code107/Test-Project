# Test Plan: redemption-analytics-advanced

_Cross-story integration tests for [spec.md](spec.md)._

_**Per-story tests** (unit tests, @WebMvcTest, Vitest, Playwright E2E) live inside each `stories/US-NN-*.md` alongside the code they verify. This file covers only tests that **span multiple stories** or require the full system to be running._

_Uses `extends AbstractLocalIntegrationTest` (Testcontainers PostgreSQL 16)._
_Path: `src/test/java/com/tenxengage/app/integration/`_

---

## Business Rule Enforcement

_One test per business rule from `spec.md → ## Service Layer` and edge cases. Uses a real DB — unit test mocks can mask DB constraint issues._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `AdvancedAnalyticsIntegrationTest` | `dateFrom=today`, `dateTo=today+364` (span=365d) on all JSON endpoints | 200 — at or below the 365-day cap | Foundation, all US |
| `AdvancedAnalyticsIntegrationTest` | `dateFrom=today`, `dateTo=today+365` (span=366d) on `/item-breakdown` | 422 with message "Date range must not exceed 365 days" | Foundation, US-01 |
| `AdvancedAnalyticsIntegrationTest` | span=366d on `/segment-breakdown`, `/time-to-first-redemption`, `/trend`, `/liability-trend`, `/liability-trend/export`, `/failure-breakdown` | 422 on all | Foundation, all US |
| `AdvancedAnalyticsIntegrationTest` | Feature flag `redemption_analytics_advanced=false` (Starter tenant) → `GET /advanced/item-breakdown` | 403 — flag gate enforced before service | Foundation |
| `AdvancedAnalyticsIntegrationTest` | Feature flag `redemption_analytics_advanced=false` → all 8 `/advanced/**` endpoints | 403 on each | Foundation |
| `AdvancedAnalyticsIntegrationTest` | `isStale` logic: `analytics_mv_refresh_log` has `last_refreshed_at = NOW()-5h` → `/refresh-status` | `isStale=true`, `lastRefreshedAt` present | Foundation, US-05 |
| `AdvancedAnalyticsIntegrationTest` | `isStale` logic: `analytics_mv_refresh_log` is empty → `/refresh-status` | `isStale=true`, `lastRefreshedAt=null` | Foundation, US-05 |
| `AdvancedAnalyticsIntegrationTest` | `isStale` logic: `analytics_mv_refresh_log` has `last_refreshed_at = NOW()-1h` → `/refresh-status` | `isStale=false` | Foundation, US-05 |

---

## Contract Conformance

_Verifies actual response body shapes and status codes match the generated OpenAPI contract. Catches BE drift that per-story `@WebMvcTest` cannot._

_Uses RestAssured + OpenAPI validator wired to `../tenxengage-contracts/endpoints/redemption-analytics-advanced.yaml`._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/refresh-status` response shape | 200; body matches `AnalyticsRefreshStatusResponse` contract schema; `isStale`, `lastRefreshedAt` fields present with correct types | US-05 |
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/item-breakdown` response shape | 200; body matches `ItemBreakdownResponse` contract; `items[]` array present; each item has all required fields | US-01 |
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/segment-breakdown` response shape | 200; body matches `SegmentBreakdownResponse` contract | US-02 |
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/time-to-first-redemption` response shape | 200; body matches `TimeToFirstRedemptionResponse` contract; null `avgHoursToFirstRedemption` accepted when `sampleCount=0` | US-03 |
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/trend` response shape | 200; body matches `RedemptionTrendResponse` contract | US-04 |
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/liability-trend` response shape | 200; body matches `LiabilityTrendResponse` contract | US-06 |
| `AdvancedAnalyticsContractConformanceTest` | `GET /advanced/failure-breakdown` response shape | 200; body matches `FailureBreakdownResponse` contract | US-07 |
| `AdvancedAnalyticsContractConformanceTest` | 422 error response shape (span > 365d) | 422 body matches contract `ErrorResponse` schema; message field present | Foundation |
| `AdvancedAnalyticsContractConformanceTest` | 429 rate-limit response shape on `/liability-trend/export` | 429 body matches rate-limit contract; `Retry-After` header present | US-06 |
| `AdvancedAnalyticsContractConformanceTest` | 403 response shape (missing permission) | 403 body matches contract `ErrorResponse` schema | Foundation |

---

## Tenant Isolation & Security

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `AdvancedAnalyticsIntegrationTest` | Unauthenticated `GET /advanced/item-breakdown` | 401; no body leakage; not 403 or 500 | US-01 |
| `AdvancedAnalyticsIntegrationTest` | Unauthenticated `GET /advanced/refresh-status` | 401 | US-05 |
| `AdvancedAnalyticsIntegrationTest` | CLIENT_ADMIN Tenant A queries `/advanced/item-breakdown` — Tenant B has rows in `mv_item_redemption_breakdown` | Tenant A result set contains ONLY Tenant A rows; 0 rows from Tenant B | US-01 |
| `AdvancedAnalyticsIntegrationTest` | CLIENT_ADMIN Tenant A queries `/advanced/liability-trend/export` — Tenant B has liability rows | CSV contains ONLY Tenant A rows | US-06 |
| `AdvancedAnalyticsIntegrationTest` | CLIENT_ADMIN role → `GET /advanced/item-breakdown` | 200 | US-01 |
| `AdvancedAnalyticsIntegrationTest` | PARTNER_ADMIN role → `GET /advanced/item-breakdown` | 403 — `action.redemption.analytics.advanced` not granted to PARTNER_ADMIN | US-01 |
| `AdvancedAnalyticsIntegrationTest` | PARTNER_ADMIN role → all 8 `/advanced/**` endpoints | 403 on each | Foundation, all US |

---

## Audit & Events

_Audit rows for successful AND failed export operations. Per-story unit tests verify fire-and-forget; this section verifies the full chain and negative paths._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `AdvancedAnalyticsIntegrationTest` | Successful `GET /advanced/liability-trend/export` (200) | `audit_log` has record: `action=DATA_EXPORTED`, `resource_type=REDEMPTION_ADVANCED_ANALYTICS_EXPORT`, correct `actor_id`, `tenant_id`, and `metadata.rowCount` | US-06 |
| `AdvancedAnalyticsIntegrationTest` | Failed export — 422 (span > 365d) | `audit_log` count unchanged after the 422; failed ops must not leak audit records | US-06 |
| `AdvancedAnalyticsIntegrationTest` | Failed export — 429 (rate limit exceeded) | `audit_log` count unchanged; 429 does not generate an audit entry | US-06 |
| `AdvancedAnalyticsIntegrationTest` | Failed export — 403 (permission missing) | `audit_log` count unchanged | US-06 |
| `AdvancedAnalyticsIntegrationTest` | `GET /advanced/item-breakdown` (read-only, no audit spec'd) | `audit_log` count unchanged — read-only endpoints produce no audit entries | US-01 |

---

## Query Correctness at Scale

_Proves tenant-scoping and filter correctness with realistic multi-tenant data volumes._

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `AdvancedAnalyticsMvQueryIT` | 10 tenants with seeded `mv_item_redemption_breakdown` rows; query as Tenant 3 | Response items all have `client_id = Tenant3`; items from Tenants 1–2 and 4–10 absent | Foundation, US-01 |
| `AdvancedAnalyticsMvQueryIT` | `mv_item_redemption_breakdown` with 100 rows for Tenant A; query with `?region=APAC` | Only rows where `region='APAC'` returned | Foundation, US-01 |
| `AdvancedAnalyticsMvQueryIT` | `mv_segment_redemption_breakdown` with region=APAC AND role=MANAGER filter | Only rows matching both predicates | Foundation, US-02 |
| `AdvancedAnalyticsMvQueryIT` | `mv_liability_trend` with 365 rows (one per day); query with `dateFrom=D-30&dateTo=D` | Returns exactly 30 rows ordered by `period_date ASC` | Foundation, US-06 |
| `AdvancedAnalyticsMvQueryIT` | `mv_item_redemption_breakdown` rows sorted by `total_redeemed_count` desc | First row has highest `total_redeemed_count`; last row has lowest | Foundation, US-01 |

---

## E2E Cross-Story Scenarios (Real Stack)

_Playwright scenarios run against a real running backend. No `page.route()` mocking. Catches BE-FE shape drift._

_Setup: `beforeAll` creates required state via real API calls using a test-tenant JWT._

| Spec File | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `e2e/redemption-analytics-advanced/full-happy-path.spec.ts` | CLIENT_ADMIN opens Advanced tab → applies region filter → all 6 sections update → data visible in each section against real BE | All 6 sections render data rows (non-empty); "Data as of {timestamp}" captions visible in each section | All US |
| `e2e/redemption-analytics-advanced/full-happy-path.spec.ts` | CLIENT_ADMIN triggers CSV export → verifies download filename, CSV header, and at least one data row | Download filename = `redemption-liability-trend.csv`; CSV header = `period_date,currency_type,total_unredeemed_balance` | US-06 |
| `e2e/redemption-analytics-advanced/cross-tenant-isolation.spec.ts` | Tenant B logs in after Tenant A seeded data — Tenant B Advanced tab sections show no Tenant A data | Each section shows "No data for the selected period" OR Tenant B's own data (zero Tenant A rows) | All US |

---

## Cross-Cutting Checks

| Test Class | Scenario | Expected | Depends on Stories |
|---|---|---|---|
| `AdvancedAnalyticsCrossCuttingIT` | Rate limit: 4th export request within 60s to `/advanced/liability-trend/export` | 429 with `Retry-After` header; 4th response body matches rate-limit contract | US-06 |
| `AdvancedAnalyticsCrossCuttingIT` | Rate limit: 11th query request within 60s to `/advanced/item-breakdown` | 429 — `RateLimitFilter` (10 req/min per tenant) fires on the 11th request | US-01 |
| `AdvancedAnalyticsCrossCuttingIT` | Redis cache: first call to `/advanced/item-breakdown` loads from DB; second call within 60s is cache hit | Second call returns same response body; assert DB query count unchanged (use query count via JdbcTemplate spy) | US-01 |
| `AdvancedAnalyticsCrossCuttingIT` | No PII in logs: capture INFO+DEBUG logs during `GET /advanced/item-breakdown` with PII-bearing tenant data | Log output does NOT contain wallet balance values or partner email addresses | Foundation |
