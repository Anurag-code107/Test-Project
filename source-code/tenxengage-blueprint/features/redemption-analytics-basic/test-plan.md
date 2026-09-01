# Test Plan — redemption-analytics-basic

_Cross-story integration tests. These complement the per-story unit tests and @WebMvcTest cases — they verify the full stack behaves correctly when BE and FE work together and when tenant isolation or audit guarantees are at stake._

---

## Scope

| Covered stories | US-01, US-02 |
|---|---|
| **Total integration scenarios** | 11 |
| **Test types** | Contract conformance (API), Tenant isolation (IT), Audit verification (IT), Query accuracy (IT), E2E full-flow (Playwright) |

---

## BE Integration Test Files

All files live in `src/test/java/com/tenxengage/app/integration/redemption/`

---

### RedemptionAnalyticsContractConformanceTest

_Tests that request/response shapes match the OpenAPI contract. No mocking of service — uses a real test DB seeded with known data._

| # | Scenario | AC | Asserts |
|---|---|---|---|
| T-01 | `GET /analytics` with valid `dateFrom` + `dateTo` → 200; body matches `RedemptionAnalyticsSummaryResponse` schema | US-01 AC-1 | `redemptionRates`, `unredeemedBalances`, `failedCancelledRates` each `List`, `totalRedemptionCount` non-null; `dateWindow.from` = requested dateFrom |
| T-02 | `GET /analytics` with malformed date param (`dateFrom=not-a-date`) → 400; error body matches platform error envelope | US-01 AC-5 | Status 400; `errorCode` field present; no stack trace in body |
| T-03 | `GET /analytics` with `dateFrom` after `dateTo` → 422 | US-01 AC-5 | Status 422; `message` contains rejection reason |
| T-04 | `GET /analytics/export` with valid CLIENT_ADMIN JWT → 200; `Content-Type: text/csv`; `Content-Disposition: attachment; filename="redemption-unredeemed-balances.csv"` | US-02 AC-1 | Headers exact-match; body starts with `userId,userName,companyId,companyName,currencyType,availableBalance,reservedBalance` |
| T-05 | `GET /analytics/export` with non-CLIENT_ADMIN JWT (PARTNER_SELLER) → 403 | US-02 AC-4 | Status 403; no CSV body |
| T-06 | `GET /analytics/export` when tenant export count ≥ 3 within 60s → 429 with `Retry-After` | US-02 AC-4 | Status 429; `Retry-After` header present and ≥ 1 |

---

### RedemptionAnalyticsIsolationIT

_Multi-tenant isolation and rate limit enforcement. Uses two distinct tenant seeds._

| # | Scenario | AC | Asserts |
|---|---|---|---|
| T-07 | Tenant A CLIENT_ADMIN calls `GET /analytics` with Tenant A JWT; response contains only Tenant A ledger entries (verified by currencyId distinctness seeded only in Tenant A) | US-01 AC-1 | `redemptionRates` contains Tenant A currencyIds only; Tenant B currencyIds absent |
| T-08 | `GET /analytics` with no JWT → 401; no body leakage | US-01 AC-5 | Status 401; response body is platform-standard error, no entity data |
| T-09 | 11 sequential `GET /analytics` calls from the same user within 60s → 11th returns 429 | US-01 AC-5 | 10 succeed (200); 11th is 429 |
| T-10 | 4 sequential `GET /analytics/export` calls from the same tenant within 60s → 4th returns 429 | US-02 AC-4 | 3 succeed (200); 4th is 429 |

---

### RedemptionAnalyticsAuditIT

_Verifies that audit rows are written only on the correct operations._

| # | Scenario | AC | Asserts |
|---|---|---|---|
| T-11a | `GET /analytics/export` → 200 → audit table has 1 row: `action=DATA_EXPORTED`, `resourceType=REDEMPTION_ANALYTICS_EXPORT`, `actorId=calling user` | US-02 AC-3 | Row count +1 after call; all three fields match |
| T-11b | `GET /analytics` → 200 → audit table unchanged (read-only; no audit written) | US-01 AC-1 | Audit row count before and after GET analytics = same |
| T-11c | `GET /analytics/export` → 403 → audit table unchanged | US-02 AC-3 | Audit row count before and after 403 = same |

---

### RedemptionAnalyticsQueryIT

_Aggregation accuracy against real DB inserts. Cache disabled via test application property `spring.data.redis.ttl.redemption-analytics=0` or equivalent._

| # | Scenario | AC | Asserts |
|---|---|---|---|
| T-12 | Insert 4 REWARD LedgerEntry rows (amounts: 100, 200, 100, 50) and 2 REDEMPTION rows (amounts: 100, 100) for `currencyId="CASH"`. Call `GET /analytics`. Assert `redemptionRates[0].ratePercentage = 44.44` (200/450 × 100 = 44.44) | US-01 AC-2 | `ratePercentage` matches expected value within 2 decimal places; lifetime LedgerEntry data is unaffected by date params |

---

## FE E2E Test Files

All files live in `e2e/redemption-analytics-basic/`

---

### full-flow.spec.ts

_End-to-end scenario hitting real BE (no mocked API). Requires seeded test data for the test tenant._

| # | Scenario | Stories | Asserts |
|---|---|---|---|
| T-13 ✅ | `'full flow: load dashboard → filter → export'` | US-01, US-02 | 1. Login as CLIENT_ADMIN → navigate to `/redemption/admin/analytics` → all 4 card groups visible with data (not skeleton, not empty state) <br> 2. Click "Last 7 days" preset → API refetch triggered → windowed cards update (fail/cancelled rate may change) <br> 3. Click "Export" → confirm dialog → "Download CSV" → Playwright `waitForEvent('download')` confirms file download; filename = `redemption-unredeemed-balances.csv` |

---

## Test execution order

```
RedemptionAnalyticsContractConformanceTest  (depends on: US-01 BE, US-02 BE)
  └── T-01 through T-06

RedemptionAnalyticsIsolationIT             (depends on: US-01 BE, US-02 BE)
  └── T-07 through T-10

RedemptionAnalyticsAuditIT                 (depends on: US-02 BE)
  └── T-11a, T-11b, T-11c

RedemptionAnalyticsQueryIT                 (depends on: US-01 BE, F3)
  └── T-12

full-flow.spec.ts                          (depends on: US-01 FE + BE, US-02 FE + BE)
  └── T-13
```

_Run integration tests (T-01 to T-12) in the BE session after US-02 BE is merged. Run full-flow.spec.ts in the FE session after US-02 FE is merged._

---

## Coverage matrix

| AC | Covered by |
|---|---|
| US-01 AC-1 | T-01, T-07, T-11b, T-12 |
| US-01 AC-2 | T-12 |
| US-01 AC-3 | T-12 (via dateFrom/dateTo Instant conversion — accurate windowed results) |
| US-01 AC-4 | _Service unit test (per-story) — Redis caching not verified in IT (requires Redis in CI)_ |
| US-01 AC-5 | T-02, T-03, T-08, T-09 |
| US-01 AC-6 | _E2E per-story (analytics-dashboard.spec.ts Scenario 3)_ |
| US-01 AC-7 | _E2E per-story (analytics-dashboard.spec.ts Scenario 2)_ |
| US-02 AC-1 | T-04, T-13 |
| US-02 AC-2 | _Service unit test (per-story) + T-13 (visual file download)_ |
| US-02 AC-3 | T-11a, T-11b, T-11c |
| US-02 AC-4 | T-05, T-06, T-10 |
| US-02 AC-5 | _E2E per-story (export-csv.spec.ts Scenarios 1–2) + T-13_ |

---

## Notes

- **Redis in CI:** If the CI environment does not have Redis, AC-4 (cache) is verified only by the service unit test. Add a note in the tracker when promoting to `T1 = done` if Redis was absent.
- **Test DB seed:** `RedemptionAnalyticsIsolationIT` (T-07) requires two distinct tenant seeds with non-overlapping currencyId sets. Use the existing `ClientFixtures` pattern; add analytics-specific seed data in a `RedemptionAnalyticsFixtures.java` file.
- **Clock control:** T-09 and T-10 (rate limit enforcement) require controlling wall time. If the existing rate limit mechanism uses Redis with real TTLs, mock the clock or use a dedicated test rate limit bucket with TTL = 10s seeded with 9 prior hits.
- **Playwright clock:** E2E Scenario 2 in `export-csv.spec.ts` uses Playwright's fake timers (`page.clock.install()`) to advance by 45s and assert button re-enable — verify Playwright version supports `page.clock`.
